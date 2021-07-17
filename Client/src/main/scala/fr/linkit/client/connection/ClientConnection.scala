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

package fr.linkit.client.connection

import fr.linkit.api.connection.network.{ExternalConnectionState, Network}
import fr.linkit.api.connection.packet.channel.ChannelScope
import fr.linkit.api.connection.packet.channel.ChannelScope.ScopeFactory
import fr.linkit.api.connection.packet.serialization.{PacketTransferResult, PacketTranslator}
import fr.linkit.api.connection.packet.traffic._
import fr.linkit.api.connection.packet.{DedicatedPacketCoordinates, Packet, PacketAttributes, PacketException}
import fr.linkit.api.connection.{ConnectionInitialisationException, ExternalConnection}
import fr.linkit.api.local.ApplicationContext
import fr.linkit.api.local.concurrency.{AsyncTask, WorkerPools, packetWorkerExecution, workerExecution}
import fr.linkit.api.local.resource.external.ResourceFolder
import fr.linkit.api.local.system.AppLogger
import fr.linkit.api.local.system.event.EventNotifier
import fr.linkit.api.local.system.security.BytesHasher
import fr.linkit.client.ClientApplication
import fr.linkit.client.local.config.ClientConnectionConfiguration
import fr.linkit.client.connection.network.ClientSideNetwork
import fr.linkit.engine.connection.network.SimpleRemoteConsole
import fr.linkit.engine.connection.packet.fundamental.ValPacket.BooleanPacket
import fr.linkit.engine.connection.packet.serialization.DefaultPacketTranslator
import fr.linkit.engine.connection.packet.traffic.{DefaultPacketReader, DynamicSocket}
import fr.linkit.engine.local.concurrency.PacketReaderThread
import fr.linkit.engine.local.system.{Rules, SystemPacket}
import fr.linkit.engine.local.utils.{NumberSerializer, ScalaUtils}
import org.jetbrains.annotations.NotNull

import scala.reflect.ClassTag
import scala.util.control.NonFatal

class ClientConnection private(session: ClientConnectionSession) extends ExternalConnection {

    import session._

    initPacketReader()

    override val supportIdentifier: String            = configuration.identifier
    override val translator       : PacketTranslator  = new DefaultPacketTranslator()
    override val port             : Int               = configuration.remoteAddress.getPort
    override val eventNotifier    : EventNotifier     = session.eventNotifier
    override val traffic          : PacketTraffic     = session.traffic
    override val boundIdentifier  : String            = serverIdentifier
    private  val sideNetwork      : ClientSideNetwork = new ClientSideNetwork(this)
    override val network          : Network           = sideNetwork
    @volatile private var alive                       = true

    /*
    * This will have for consequence to add the current connection's presence to the whole network.
    * Depending on how many clients are connected over the network, the time
    * for complete initialization between the server and all the client may vary.
    * This time is exponential.
    * */
    sideNetwork.handshake()

    override def getInjectable[C <: PacketInjectable : ClassTag](injectableID: Int, scopeFactory: ScopeFactory[_ <: ChannelScope], factory: PacketInjectableFactory[C]): C = {
        traffic.getInjectable(injectableID, scopeFactory, factory)
    }

    override def runLater(@workerExecution task: => Unit): Unit = appContext.runLater(task)

    override def runLaterControl[A](task: => A): AsyncTask[A] = appContext.runLaterControl(task)

    override def getState: ExternalConnectionState = socket.getState

    override def isAlive: Boolean = alive

    override def isConnected: Boolean = getState == ExternalConnectionState.CONNECTED

    @workerExecution
    override def shutdown(): Unit = {
        WorkerPools.ensureCurrentIsWorker("Shutdown must be performed in a contextual thread pool.")
        if (!alive)
            return //already shutdown

        readThread.close()
        appContext.unregister(this)

        traffic.close()
        socket.close()

        alive = false
    }

    @workerExecution
    private def initPacketReader(): Unit = {
        WorkerPools.ensureCurrentIsWorker("Can't start in a non worker pool !")
        if (alive)
            throw new IllegalStateException(s"Connection already started ! ($supportIdentifier)")
        alive = true

        socket.addConnectionStateListener(tryReconnect)
        readThread.onPacketRead = (result) => {
            try {
                val coordinates: DedicatedPacketCoordinates = result.coords match {
                    case d: DedicatedPacketCoordinates => d
                    case _                             => throw new IllegalArgumentException("Packet must be dedicated to this connection.")
                }
                handlePacket(result.packet, result.attributes, coordinates)
            } catch {
                case NonFatal(e) => throw new PacketException(s"Could not deserialize '${ScalaUtils.toPresentableString(result.bytes)}'", e)
            }
        }
        readThread.start()
    }

    @packetWorkerExecution //So the runLater must be specified in order to perform network operations
    private def tryReconnect(state: ExternalConnectionState): Unit = {
        val bytes         = supportIdentifier.getBytes()
        val welcomePacket = NumberSerializer.serializeInt(bytes.length) ++ bytes

        if (state == ExternalConnectionState.CONNECTED && socket.isOpen) runLater {
            socket.write(welcomePacket) //The welcome packet will let the server continue its socket handling
            systemChannel.nextPacket[BooleanPacket]
            sideNetwork.update()
            translator.initNetwork(network)
        }
    }

    private def handleSystemPacket(system: SystemPacket, coords: DedicatedPacketCoordinates): Unit = {
        val order  = system.order
        val reason = system.reason.reversedPOV()
        val sender = coords.senderID
        SimpleRemoteConsole

        import fr.linkit.engine.local.system.SystemOrder._
        order match {
            case CLIENT_CLOSE => shutdown()
            //FIXME case ABORT_TASK => tasksHandler.skipCurrent(reason)

            //FIXME weird use of exceptions/remote print
            case SERVER_CLOSE =>
            //FIXME UnexpectedPacketException(s"System packet order '$order' couldn't be handled by this connection : Received forbidden order")
            //        .printStackTrace(getConsoleErr(sender))

            case _ => //FIXME UnexpectedPacketException(s"System packet order '$order' couldn't be handled by this connection : Unknown order")
            // .printStackTrace(getConsoleErr(sender))
        }
    }

    private def handlePacket(packet: Packet, attributes: PacketAttributes, coordinates: DedicatedPacketCoordinates): Unit = {
        packet match {
            //FIXME case init: TaskInitPacket => tasksHandler.handlePacket(init, coordinates)
            case system: SystemPacket => handleSystemPacket(system, coordinates)
            case _: Packet            =>
                //println(s"START OF INJECTION ($packet, $coordinates, $number) - ${Thread.currentThread()}")
                traffic.processInjection(packet, attributes, coordinates)
            //println(s"ENT OF INJECTION ($packet, $coordinates, $number) - ${Thread.currentThread()}")
        }
    }

    override def getApp: ApplicationContext = appContext
}

object ClientConnection {

    @throws[ConnectionInitialisationException]("If Something went wrong during the initialization.")
    @NotNull
    def open(socket: ClientDynamicSocket,
             context: ClientApplication,
             configuration: ClientConnectionConfiguration): ClientConnection = {

        //Initializing values that will be used for packet transactions during the initialization.
        val translator   = new DefaultPacketTranslator()
        val packetReader = new DefaultPacketReader(socket, BytesHasher.inactive, context, translator)

        //WelcomePacket informational fields
        val identifier          = configuration.identifier
        val translatorSignature = translator.signature
        val hasherSignature     = configuration.hasher.signature

        //Aliases
        val IDbytes   = identifier.getBytes()
        val separator = Rules.WPArgsSeparator

        //Creating and sending welcomePacket
        val bytes         = IDbytes ++ separator ++ translatorSignature ++ separator ++ hasherSignature
        val welcomePacket = NumberSerializer.serializeInt(bytes.length) ++ bytes
        socket.write(welcomePacket)

        //Ensuring that the server has accepted this connection's signatures based on the previously sent welcome packet.
        packetReader.nextPacket(assertAccepted(socket, packetReader))

        //Instantiating connection instances...
        var connection: ClientConnection = null

        //Server should send a packet which provides its identifier.
        //This packet concludes phase 1 of connection's initialization.
        packetReader.nextPacketSync(result => {
            //The contains the server identifier bytes' string
            val serverIdentifier = new String(result.bytes)
            socket.identifier = serverIdentifier

            //Constructing connection instance session
            AppLogger.info(s"${identifier}: Stage 1 completed : Connection seems able to support this server configuration.")
            val readThread  = new PacketReaderThread(packetReader, serverIdentifier)
            val sessionInfo = ClientConnectionSessionInfo(context, configuration, readThread)
            val session     = ClientConnectionSession(socket, sessionInfo, serverIdentifier)
            //Constructing connection instance...
            //Stage 2 will be completed into ClientConnection constructor.
            connection = new ClientConnection(session)
            AppLogger.info(s"$identifier: Stage 3 completed : ClientSideNetwork and Connection instances created.")
        })

        //The server couldn't send the packet.
        if (connection == null)
            throw new ConnectionInitialisationException("Couldn't receive server identifier's packet. The connection have no choice to abort this initialization.")
        //return the connection instance
        connection
    }

    private def assertAccepted(socket: DynamicSocket, reader: PacketReader)(result: PacketTransferResult): Unit = {
        val bytes  = result.bytes
        val header = bytes(0)
        if (bytes.length != 1 || (header != Rules.ConnectionAccepted && header != Rules.ConnectionRefused))
            throw new ConnectionInitialisationException(s"Received unexpected welcome packet verdict format (received: ${new String(bytes)}")

        val isAccepted = header == Rules.ConnectionAccepted
        if (!isAccepted) {
            reader.nextPacket((result) => {
                val msg        = new String(result.bytes)
                val serverPort = socket.remoteSocketAddress().getPort
                throw new ConnectionInitialisationException(s"Server (port: $serverPort) refused connection: $msg")
            })
        }
    }

}
