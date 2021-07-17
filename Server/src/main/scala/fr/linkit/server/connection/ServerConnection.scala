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

import fr.linkit.api.connection.CentralConnection
import fr.linkit.api.connection.network.Network
import fr.linkit.api.connection.packet.channel.ChannelScope
import fr.linkit.api.connection.packet.channel.ChannelScope.ScopeFactory
import fr.linkit.api.connection.packet.serialization.PacketTranslator
import fr.linkit.api.connection.packet.traffic.{PacketInjectable, PacketInjectableFactory, PacketTraffic}
import fr.linkit.api.connection.packet.{DedicatedPacketCoordinates, Packet, PacketAttributes}
import fr.linkit.api.local.ApplicationContext
import fr.linkit.api.local.concurrency.{AsyncTask, WorkerPools, workerExecution}
import fr.linkit.api.local.resource.external.ResourceFolder
import fr.linkit.api.local.system.AppLogger
import fr.linkit.api.local.system.event.EventNotifier
import fr.linkit.engine.connection.packet.serialization.DefaultPacketTranslator
import fr.linkit.engine.connection.packet.traffic.DynamicSocket
import fr.linkit.engine.local.concurrency.pool.BusyWorkerPool
import fr.linkit.engine.local.system.Rules
import fr.linkit.engine.local.system.event.DefaultEventNotifier
import fr.linkit.engine.local.system.fsa.LocalFileSystemAdapters
import fr.linkit.engine.local.utils.NumberSerializer.serializeInt
import fr.linkit.server.local.config.{AmbiguityStrategy, ServerConnectionConfiguration}
import fr.linkit.server.connection.network.ServerSideNetwork
import fr.linkit.server.connection.packet.ServerPacketTraffic
import fr.linkit.server.{ServerApplication, ServerException}
import org.jetbrains.annotations.Nullable

import java.net.{ServerSocket, SocketException}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

class ServerConnection(applicationContext: ServerApplication,
                       val configuration: ServerConnectionConfiguration) extends CentralConnection {

    override val currentIdentifier : String                     = configuration.identifier
    override val translator        : PacketTranslator           = configuration.translator
    override val port              : Int                        = configuration.port
    private  val workerPool        : BusyWorkerPool             = new BusyWorkerPool(configuration.nWorkerThreadFunction(0), currentIdentifier)
    private  val serverSocket      : ServerSocket               = new ServerSocket(configuration.port)
    private  val connectionsManager: ExternalConnectionsManager = new ExternalConnectionsManager(this)
    override val traffic           : PacketTraffic              = new ServerPacketTraffic(this)
    override val eventNotifier     : EventNotifier              = new DefaultEventNotifier
    private  val sideNetwork       : ServerSideNetwork          = new ServerSideNetwork(this)(traffic)
    override val network           : Network                    = sideNetwork

    @volatile private var alive = false

    override def getApp: ApplicationContext = applicationContext

    @workerExecution
    override def shutdown(): Unit = {
        WorkerPools.ensureCurrentIsWorker("Must shutdown server connection in a worker thread.")
        if (!alive)
            return
        alive = false

        val port = configuration.port

        AppLogger.info(s"Server '$currentIdentifier' on port $port prepares to shutdown...")
        applicationContext.unregister(this)

        connectionsManager.close()
        AppLogger.info(s"Server '$currentIdentifier' shutdown.")
    }

    @workerExecution
    def start(): Unit = {
        WorkerPools.ensureCurrentIsWorker("Must start server connection in a worker thread.")
        if (alive)
            throw new ServerException(this, "Server is already started.")
        AppLogger.info(s"Server '$currentIdentifier' starts on port ${configuration.port}")
        AppLogger.trace(s"Identifier Ambiguity Strategy : ${configuration.identifierAmbiguityStrategy}")

        try {
            loadSocketListener()
        } catch {
            case NonFatal(e) =>
                AppLogger.printStackTrace(e)
                shutdown()
        }
    }

    override def isAlive: Boolean = alive

    override def getInjectable[C <: PacketInjectable : ClassTag](injectableID: Int, scopeFactory: ScopeFactory[_ <: ChannelScope], factory: PacketInjectableFactory[C]): C = {
        traffic.getInjectable(injectableID, scopeFactory, factory)
    }

    override def getConnection(identifier: String): Option[ServerExternalConnection] = Option(connectionsManager.getConnection(identifier))

    override def countConnections: Int = connectionsManager.countConnections

    override def runLaterControl[A](task: => A): AsyncTask[A] = workerPool.runLaterControl(task)

    override def runLater(task: => Unit): Unit = workerPool.runLater(task)

    def broadcastPacket(packet: Packet, attributes: PacketAttributes, sender: String, injectableID: Int, discarded: String*): Unit = {
        if (connectionsManager.countConnections - discarded.length < 0) {
            // There is nowhere to send this packet.
            return
        }
        connectionsManager.broadcastPacket(packet, attributes, injectableID, sender, discarded: _*)
        if (!discarded.contains(currentIdentifier)) {
            traffic.processInjection(packet, attributes, DedicatedPacketCoordinates(injectableID, currentIdentifier, sender))
        }
    }

    private[connection] def getSideNetwork: ServerSideNetwork = sideNetwork

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////  C L I E N T  I N I T I A L I S A T I O N  H A N D L I N G  /////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //TODO Documentation

    private def listenSocketConnection(): Unit = {
        val socketContainer = new SocketContainer(true)
        val port = configuration.port
        AppLogger.debug(s"Ready to accept next connection on port $port")

        try {
            val socket = serverSocket.accept()
            if (!alive)
                return
            socketContainer.set(socket)
        } catch {
            case e: SocketException =>
                val msg = e.getMessage.toLowerCase
                if (msg == "socket closed" || msg == "socket is closed")
                    return
                Console.err.println(msg)
                onException(e)
            case NonFatal(e)        =>
                AppLogger.printStackTrace(e)
                onException(e)
        }

        AppLogger.debug(s"Socket accepted (${socketContainer.getCurrent})")
        runLater {
            AppLogger.trace(s"Handling client socket ${socketContainer.getCurrent}...")
            val count = connectionsManager.countConnections
            handleSocket(socketContainer)
            val newCount = connectionsManager.countConnections
            if (count != newCount) {
                workerPool.setThreadCount(configuration.nWorkerThreadFunction(newCount))
            }
        }

        def onException(e: Throwable): Unit = runLater {
            sendRefusedConnection(socketContainer, s"An exception occurred in server during client connection initialisation ($e)") //sends a negative response for the fr.linkit.client initialisation handling
            //shutdown()
        }
    }

    /**
     * Reads a welcome packet from a connection.<br>
     * The Welcome packet is the first packet that a connection must send
     * in order to provide its identifier to the server and its hasher & translator signature.
     *
     * @return the identifier bound with the socket
     * */
    private def readWelcomePacket(socket: SocketContainer): String = {
        val welcomePacketLength = socket.readInt()
        val welcomePacket       = socket.read(welcomePacketLength)
        new String(welcomePacket)
    }

    /**
     * @return a [[WelcomePacketVerdict]] if the scan that decides if the connection that sends
     *         this welcomePacket should be discarded by the server.
     * */
    private def scanWelcomePacket(welcomePacket: String): WelcomePacketVerdict = {
        val args = welcomePacket.split(";")
        if (args.length != Rules.WPArgsLength)
            return WelcomePacketVerdict(null, false, s"Arguments length does not conform to server's rules of ${Rules.WPArgsLength}")
        try {
            val identifier          = args(0)
            val translatorSignature = args(1)
            val hasherSignature     = args(2)

            if (!(configuration.hasher.signature sameElements hasherSignature))
                return WelcomePacketVerdict(identifier, false, "Hasher signatures mismatches !")

            if (!(translator.signature sameElements translatorSignature))
                return WelcomePacketVerdict(identifier, false, "Translator signatures mismatches !")

            if (!Rules.IdentifierPattern.matcher(identifier).matches())
                return WelcomePacketVerdict(identifier, false, "Provided identifier does not matches server's rules.")

            WelcomePacketVerdict(identifier, true)
        } catch {
            case NonFatal(e) =>
                AppLogger.printStackTrace(e)
                WelcomePacketVerdict(null, false, e.getMessage)
        }
    }

    private def handleSocket(socket: SocketContainer): Unit = {
        val welcomePacket = readWelcomePacket(socket)
        val verdict       = scanWelcomePacket(welcomePacket)
        if (!verdict.accepted) {
            verdict.concludeRefusal(socket)
            return
        }
        AppLogger.info("Stage 1 Completed : Connection seems able to support this server configuration.")
        val identifier = verdict.identifier
        socket.identifier = identifier
        handleNewConnection(identifier, socket)
    }

    private def loadSocketListener(): Unit = {
        val thread = new Thread(() => {
            alive = true
            while (alive) listenSocketConnection()
        })
        thread.setName(s"Socket Listener : ${configuration.port}")
        thread.start()
    }

    private def handleNewConnection(identifier: String,
                                    socket: SocketContainer): Unit = {

        val currentConnection = getConnection(identifier)
        //There is no currently connected connection with the same identifier on this network.
        if (currentConnection.isEmpty) {
            connectionsManager.registerConnection(identifier, socket)
            return
        }

        handleConnectionIdAmbiguity(currentConnection.get, socket)
    }

    private def handleConnectionIdAmbiguity(conflicted: ServerExternalConnection,
                                            socket: SocketContainer): Unit = {
        val identifier = conflicted.boundIdentifier
        if (!conflicted.isConnected) {
            conflicted.updateSocket(socket.getCurrent)
            sendAuthorisedConnection(socket)
            AppLogger.info(s"The connection of ${conflicted.boundIdentifier} has been resumed.")
            return
        }
        val strategy = configuration.identifierAmbiguityStrategy
        AppLogger.trace(s"Connection '$identifier' conflicts with socket $socket. Applying Ambiguity Strategy '$strategy'...")

        val rejectMsg = s"Another relay point with id '$identifier' is currently connected on the targeted network."
        import AmbiguityStrategy._
        configuration.identifierAmbiguityStrategy match {
            case CLOSE_SERVER =>
                sendRefusedConnection(socket, rejectMsg + " Consequences: Closing Server...")
                //broadcastMessage(true, "RelayServer will close your connection because of a critical error")
                shutdown()

            case REJECT_NEW =>
                Console.err.println("Rejected connection of a client because it gave an already registered relay identifier.")
                sendRefusedConnection(socket, rejectMsg)

            case REPLACE =>
                connectionsManager.unregister(identifier).get.shutdown()
                connectionsManager.registerConnection(identifier, socket)
            //The connection initialisation packet isn't sent here because it is send into the registerConnection method.

            case DISCONNECT_BOTH =>
                connectionsManager.unregister(identifier).get.shutdown()
                sendRefusedConnection(socket, rejectMsg + " Consequences : Disconnected both")
        }
    }

    private[connection] def sendAuthorisedConnection(socket: DynamicSocket): Unit = {
        socket.write(serializeInt(1) ++ Array(Rules.ConnectionAccepted))
        val bytes = currentIdentifier.getBytes()
        socket.write(serializeInt(bytes.length) ++ bytes)
    }

    private[connection] def sendRefusedConnection(socket: DynamicSocket, message: String): Unit = {
        socket.write(serializeInt(1) ++ Array(Rules.ConnectionRefused))
        val bytes = message.getBytes()
        socket.write(serializeInt(bytes.length) ++ bytes)
    }

    private case class WelcomePacketVerdict(@Nullable("bad-packet-format") identifier: String,
                                            accepted: Boolean,
                                            @Nullable("accepted=true") refusalMessage: String = null) {

        def concludeRefusal(socket: DynamicSocket): Unit = {
            if (identifier == null) {
                AppLogger.error(s"An unknown connection have been discarded: $refusalMessage")
            } else {
                AppLogger.error(s"Connection $identifier has been discarded: $refusalMessage")
            }
            sendRefusedConnection(socket, s"Connection discarded by the server: $refusalMessage")
        }
    }

}
