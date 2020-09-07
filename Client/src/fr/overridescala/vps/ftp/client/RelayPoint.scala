package fr.overridescala.vps.ftp.client

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

import fr.overridescala.vps.ftp.api.Relay
import fr.overridescala.vps.ftp.api.packet.SimplePacketChannel
import fr.overridescala.vps.ftp.api.task.tasks._
import fr.overridescala.vps.ftp.api.task.{Task, TasksHandler}
import fr.overridescala.vps.ftp.api.transfer.{TransferDescription, TransferableFile}
import fr.overridescala.vps.ftp.api.utils.{Constants, Protocol}

class RelayPoint(private val serverAddress: InetSocketAddress,
                 private val id: String) extends Relay {


    private val socketChannel = configSocket()
    private val tasksHandler = new TasksHandler()
    private val packetChannel = new SimplePacketChannel(socketChannel)
    private val completerFactory = new RelayPointTaskCompleterFactory(tasksHandler)

    override val identifier: String = id

    override def doDownload(description: TransferDescription): Task[Unit] =
        new DownloadTask(packetChannel, tasksHandler, description)

    override def doUpload(description: TransferDescription): Task[Unit] =
        new UploadTask(packetChannel, tasksHandler, description)

    override def requestAddress(id: String): Task[InetSocketAddress] =
        new AddressTask(packetChannel, tasksHandler, id)

    override def requestFileInformation(owner: InetSocketAddress, path: String): Task[TransferableFile] =
        new FileInfoTask(packetChannel, tasksHandler, owner, path)

    override def start(): Unit = new Thread(() => {
        println("ready !")

        val buffer = ByteBuffer.allocate(Constants.MAX_PACKET_LENGTH)
        //enable the task management
        tasksHandler.start()
        while (true) {
            updateNetwork(buffer)
        }
    }).start()

    override def close(): Unit = {
        socketChannel.close()
        new DisconnectTask(tasksHandler, packetChannel).completeNow()
    }

    def updateNetwork(buffer: ByteBuffer): Unit = {
        val count = socketChannel.read(buffer)
        val bytes = new Array[Byte](count)
        buffer.flip()
        buffer.get(bytes)
        val packet = Protocol.toPacket(bytes)
        if (tasksHandler.handlePacket(packet, completerFactory, packetChannel)) {
            return
        }
        packetChannel.addPacket(packet)
        buffer.clear()
    }

    def configSocket(): SocketChannel = {
        println("connecting to server...")
        val socket = SocketChannel.open(serverAddress)
        println("connected !")
        socket.configureBlocking(true)
        socket
    }

    //initial tasks
    Runtime.getRuntime.addShutdownHook(new Thread(() => close()))
    new InitTask(tasksHandler, packetChannel, identifier).queueWithError(msg => {
        println(s"unable to connect to the server : $msg")
        close()
    })

}