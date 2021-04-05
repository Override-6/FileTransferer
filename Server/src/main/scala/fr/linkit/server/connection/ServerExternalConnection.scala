/*
 * Copyright (c) 2021. Linkit and or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can only use it for personal uses, studies or documentation.
 * You can download this source code, and modify it ONLY FOR PERSONAL USE and you
 * ARE NOT ALLOWED to distribute your MODIFIED VERSION.
 *
 * Please contact maximebatista18@gmail.com if you need additional information or have any
 * questions.
 */

package fr.linkit.server.connection

import fr.linkit.api.connection.network.{ExternalConnectionState, Network}
import fr.linkit.api.connection.packet.serialization.{PacketSerializationResult, PacketTranslator}
import fr.linkit.api.connection.packet.traffic.ChannelScope.ScopeFactory
import fr.linkit.api.connection.packet.traffic.{ChannelScope, PacketInjectable, PacketInjectableFactory, PacketTraffic}
import fr.linkit.api.connection.packet.{DedicatedPacketCoordinates, Packet, PacketAttributes}
import fr.linkit.api.connection.{ConnectionException, ExternalConnection}
import fr.linkit.api.local.concurrency.workerExecution
import fr.linkit.api.local.system.AppLogger
import fr.linkit.api.local.system.event.EventNotifier
import fr.linkit.core.connection.packet.fundamental.TaskInitPacket
import fr.linkit.core.connection.packet.serialization.SimpleTransferInfo
import fr.linkit.core.local.concurrency.pool.BusyWorkerPool
import fr.linkit.core.local.system.SystemPacket
import org.jetbrains.annotations.NotNull

import java.net.Socket
import scala.reflect.ClassTag

class ServerExternalConnection private(val session: ExternalConnectionSession) extends ExternalConnection {

    import session._

    override val supportIdentifier: String           = server.supportIdentifier
    override val traffic          : PacketTraffic    = server.traffic
    override val translator       : PacketTranslator = server.translator
    override val eventNotifier    : EventNotifier    = server.eventNotifier
    override val boundIdentifier  : String           = session.boundIdentifier
    override val network          : Network          = session.network

    @volatile private var alive = false

    override def shutdown(): Unit = {
        BusyWorkerPool.ensureCurrentIsWorker()
        alive = false
        /*if (reason.isInternal && isConnected) {
            val sysChannel = session.channel
            sysChannel.sendOrder(SystemOrder.CLIENT_CLOSE, reason)
        }*/
        readThread.close()
        session.close()

        connectionManager.unregister(supportIdentifier)
        AppLogger.trace(s"Connection closed for $supportIdentifier")
    }

    override def isAlive: Boolean = alive

    override def getInjectable[C <: PacketInjectable : ClassTag](injectableID: Int, scopeFactory: ScopeFactory[_ <: ChannelScope], factory: PacketInjectableFactory[C]): C = {
        serverTraffic.getInjectable(injectableID, scopeFactory, factory)
    }

    override def getState: ExternalConnectionState = session.getSocketState

    override def runLater(callback: => Unit): Unit = {
        server.runLater(callback)
    }

    override def ensureCurrentThreadOwned(msg: String): Unit = server.ensureCurrentThreadOwned(msg)

    override def ensureCurrentThreadOwned(): Unit = server.ensureCurrentThreadOwned()

    override def isCurrentThreadOwned: Boolean = server.isCurrentThreadOwned

    def start(): Unit = {
        if (alive) {
            throw ConnectionException(this, "This Connection was already used and is now definitely closed.")
        }
        alive = true
        readThread.onPacketRead = result => {
            val coordinates: DedicatedPacketCoordinates = result.coords match {
                case d: DedicatedPacketCoordinates => d
                case _                             => throw new IllegalArgumentException("Packet must be dedicated to this connection.")
            }

            handlePacket(result.packet, result.attributes, coordinates)
        }
        readThread.start()
        //Method useless but kept because services could need to be started in the future?
    }

    def sendPacket(packet: Packet, attributes: PacketAttributes, channelID: Int): Unit = {
        runLater {
            val coords       = DedicatedPacketCoordinates(channelID, boundIdentifier, server.supportIdentifier)
            val transferInfo = SimpleTransferInfo(coords, attributes, packet)
            val result       = translator.translate(transferInfo)
            session.send(result)
        }
    }

    override def isConnected: Boolean = getState == ExternalConnectionState.CONNECTED

    private[connection] def updateSocket(socket: Socket): Unit = {
        BusyWorkerPool.ensureCurrentIsWorker()
        session.updateSocket(socket)
    }

    def send(result: PacketSerializationResult): Unit = {
        session.send(result)
    }

    private[connection] def send(bytes: Array[Byte]): Unit = {
        session.send(bytes)
    }

    @workerExecution
    private def handlePacket(packet: Packet, attributes: PacketAttributes, coordinates: DedicatedPacketCoordinates): Unit = {
        if (!alive)
            return

        packet match {
            case systemPacket: SystemPacket => handleSystemOrder(systemPacket)
            case init: TaskInitPacket       => tasksHandler.handlePacket(init, coordinates)
            case _: Packet                  =>
                serverTraffic.processInjection(packet, attributes, coordinates)
        }
    }

    private def handleSystemOrder(packet: SystemPacket): Unit = {
        val orderType = packet.order
        val reason    = packet.reason.reversedPOV()
        import fr.linkit.core.local.system.SystemOrder._
        orderType match {
            case CLIENT_CLOSE => runLater(shutdown())
            case SERVER_CLOSE => server.shutdown()
            case ABORT_TASK   => tasksHandler.skipCurrent(reason)

            case _ =>
                val msg = s"Could not complete order '$orderType', can't be handled by a server or unknown order"
                AppLogger.error(msg)
            //UnexpectedPacketException(s"Could not complete order '$orderType', can't be handled by a server or unknown order")
            //.printStackTrace(getConsoleErr)
        }
    }

}

object ServerExternalConnection {

    /**
     * Constructs a ClientConnection without starting it.
     *
     * @throws NullPointerException if the identifier or the socket is null.
     * @return a started ClientConnection.
     * @see [[SocketContainer]]
     * */
    def open(@NotNull session: ExternalConnectionSession): ServerExternalConnection = {
        if (session == null) {
            throw new NullPointerException("Unable to construct ClientConnection : session cant be null")
        }
        val connection = new ServerExternalConnection(session)
        connection.start()
        connection
    }

}