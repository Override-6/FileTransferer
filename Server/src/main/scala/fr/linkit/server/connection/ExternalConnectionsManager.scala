/*
 *  Copyright (c) 2021. Linkit and or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can only use it for personal uses, studies or documentation.
 *  You can download this source code, and modify it ONLY FOR PERSONAL USE and you
 *  ARE NOT ALLOWED to distribute your MODIFIED VERSION.
 *
 *  Please contact maximebatista18@gmail.com if you need additional information or have any
 *  questions.
 */

package fr.linkit.server.connection

import fr.linkit.api.connection.packet.serialization.{PacketDeserializationResult, PacketTransferResult, Serializer}
import fr.linkit.api.connection.packet.{DedicatedPacketCoordinates, Packet, PacketAttributes}
import fr.linkit.api.connection.{ConnectionException, NoSuchConnectionException}
import fr.linkit.api.local.system.{AppLogger, JustifiedCloseable, Reason}
import fr.linkit.engine.connection.packet.serialization.SimpleTransferInfo
import fr.linkit.engine.local.concurrency.PacketReaderThread
import fr.linkit.server.ServerException
import org.jetbrains.annotations.Nullable

import scala.collection.mutable
import scala.util.control.NonFatal

/**
 * TeamMate of RelayServer, handles the RelayPoint Connections.
 *
 * @see [[ServerConnection]]
 * @see [[ServerExternalConnection]]
 * */
class ExternalConnectionsManager(server: ServerConnection) extends JustifiedCloseable {

    /**
     * java map containing all RelayPointConnection instances
     * */
    private val connections: mutable.Map[String, ServerExternalConnection] = mutable.Map.empty
    private val maxConnection                                              = server.configuration.maxConnection

    @volatile private var closed = false

    override def close(reason: Reason): Unit = {
        for ((_, connection) <- connections) try {
            AppLogger.trace(s"Shutting down connection '${connection.boundIdentifier}'...")
            connection.shutdown()
        } catch {
            case NonFatal(e) => AppLogger.printStackTrace(e)
        }
        closed = true
    }

    /**
     * creates and register a connection.
     *
     * @param socket     the socket to start the connection
     * @param identifier the connection's identifier
     * @throws ConnectionException if the provided identifier is already taken.
     * @throws ServerException     if the registered connection count exceeded configuration limit.
     * */
    @throws[ConnectionException]("if the provided identifier is already taken")
    @throws[ServerException]("if the registered connection count exceeded configuration limit.")
    def registerConnection(identifier: String,
                           socket: SocketContainer): Unit = {
        AppLogger.trace(s"Registering connection '$identifier' (${socket.remoteSocketAddress()})...")
        //Ensure that the connection's identifier that is about to be created isn't registered yet.
        if (connections.contains(identifier))
            throw ConnectionException(connections(identifier), s"This connection identifier is taken ! ('$identifier')")

        //Ensure that fixed connection limit is not reached.
        if (connections.size > maxConnection)
            throw ServerException(server, "Maximum connection limit exceeded")

        //Opening ClientConnectionSession and finalizing registration...
        val packetReader = new SelectivePacketReader(socket, server, this, identifier)
        val readerThread = new PacketReaderThread(packetReader, identifier)
        val info         = ExternalConnectionSessionInfo(server, this, server.getSideNetwork, readerThread)

        val connectionSession = ExternalConnectionSession(identifier, socket, info)
        val connection        = ServerExternalConnection.open(connectionSession)
        AppLogger.info(s"Stage 2 completed : Connection '$identifier' created.")
        connections.put(identifier, connection)
        server.sendAuthorisedConnection(socket)

        val canConnect = true //server.configuration.checkConnection(connection)
        if (canConnect) {
            AppLogger.info(s"Stage 3 completed : Connection of '$identifier' was registered into connection manager")
            return
        }

        AppLogger.error(s"Security Manager discarded connection $identifier from the server.")

        connections.remove(identifier)
        connection.shutdown()
    }

    /* def broadcastMessage(err: Boolean, msg: String): Unit = {
         connections.values
                 .foreach(connection => {
                     if (err)
                         connection.getConsoleErr.println(msg)
                     else connection.getConsoleOut.println(msg)
                 })
     }*/

    /**
     * Broadcast bytes sequence to every connected clients
     * */
    def broadcastPacket(packet: Packet, attributes: PacketAttributes, injectableID: Int, senderID: String, discardedIDs: String*): Unit = {
        PacketReaderThread.checkNotCurrent()
        val candidates = connections.values
                .filter(con => !discardedIDs.contains(con.boundIdentifier) && con.isConnected)

        val packetCache = new mutable.HashMap[Serializer, Array[Byte]]()
        val attributesCache = new mutable.HashMap[Serializer, Array[Byte]]()

        candidates.foreach(connection => {
            val translator = connection.translator
            val connectionID = connection.boundIdentifier
            val serializer = translator.getSerializer

            if (!packetCache.contains(serializer))
                packetCache.put(serializer, translator.translatePacket(packet, connectionID))

            if (!attributesCache.contains(serializer))
                attributesCache.put(serializer, translator.translateAttributes(attributes, connectionID))

            val coords = DedicatedPacketCoordinates(injectableID, connection.boundIdentifier, senderID)
            val transferInfo = SimpleTransferInfo(coords, attributes, packet)
            val result      = translator.translate(transferInfo)
            connection.send(result)
        })
    }

    /**
     * unregisters a Relay point
     *
     * @param identifier the identifier to disconnect
     * */
    def unregister(identifier: String): Option[ServerExternalConnection] = {
        connections.remove(identifier)
    }

    /**
     * retrieves a RelayPointConnection based on the address
     *
     * @param identifier the identifier linked [[ServerExternalConnection]]
     * @return the found [[ServerExternalConnection]] bound with the identifier
     * */
    @Nullable
    def getConnection(identifier: String): ServerExternalConnection = connections.get(identifier).orNull

    def countConnections: Int = connections.size

    def listIdentifiers: Seq[String] = connections.keys.toSeq

    /**
     * determines if the address is not registered
     *
     * @param identifier the identifier to test
     * @return true if the address is not registered, false instead
     * */
    def isNotRegistered(identifier: String): Boolean = {
        !connections.contains(identifier)
    }

    /**
     * @param identifier the identifier to test
     * @return true if any connected Relay have the specified identifier
     * */
    def isRegistered(identifier: String): Boolean = {
        identifier == server.currentIdentifier || connections.contains(identifier) //reserved server identifier
    }

    override def isClosed: Boolean = closed

    /**
     * Deflects a packet to its associated [[ServerExternalConnection]]
     *
     * @throws NoSuchConnectionException if no connection where found for this packet.
     * */
    private[connection] def deflect(result: PacketTransferResult): Unit = {
        val target = result.coords match {
            case e: DedicatedPacketCoordinates => e.targetID
            case _                             => throw new IllegalArgumentException("Direct packet must be provided with DedicatedPacketCoordinates")
        }

        val connection = getConnection(target)
        if (connection == null)
            throw NoSuchConnectionException(s"unknown ID '$target' to deflect packet")
        connection.send(result.bytes)
    }
}