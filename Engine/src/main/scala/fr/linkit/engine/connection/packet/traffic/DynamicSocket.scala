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

package fr.linkit.engine.connection.packet.traffic

import fr.linkit.api.connection.network.ExternalConnectionState
import fr.linkit.api.local.concurrency.packetWorkerExecution
import fr.linkit.api.local.system.{AppException, AppLogger, IllegalCloseException, JustifiedCloseable, Reason}
import fr.linkit.engine.local.utils.{ConsumerContainer, NumberSerializer}

import java.io.{BufferedOutputStream, IOException, InputStream}
import java.net.{ConnectException, InetSocketAddress, Socket}

abstract class DynamicSocket(autoReconnect: Boolean = true) extends JustifiedCloseable {

    @volatile protected var currentSocket      : Socket               = _
    @volatile protected var currentOutputStream: BufferedOutputStream = _
    @volatile protected var currentInputStream : InputStream          = _
    //@volatile private   var totalWriteTime     : Long                 = 0

    private val listeners = ConsumerContainer[ExternalConnectionState]()

    def boundIdentifier: String

    def write(buff: Array[Byte]): Unit = {
        //val t0 = System.currentTimeMillis()
        SocketLocker.awaitConnected()
        ensureReady()
        SocketLocker.markAsWriting()
        try {
            currentOutputStream.write(buff)
            currentOutputStream.flush()
            //val t1 = System.currentTimeMillis()
            //totalWriteTime += t1 - t0
            //NETWORK-DEBUG-MARK
            AppLogger.logUpload(boundIdentifier, buff)
        } catch {
            case e@(_: ConnectException | _: IOException) =>

                if (e.getMessage.contains("socket write error")) {
                    AppLogger.printStackTrace(e)
                } else {
                    System.err.println(e)
                }

                if (isClosed || !autoReconnect)
                    return

                SocketLocker.markDisconnected()
                reconnect()
                SocketLocker.markAsConnected()
                write(buff)
        } finally {
            SocketLocker.unMarkAsWriting()
        }
    }

    override def close(reason: Reason): Unit = {
        SocketLocker.markAsClosed()

        if (!currentSocket.isClosed)
            closeCurrentStreams()
        SocketLocker.releaseAllMonitors()
        AppLogger.info(s"All monitors that were waiting the socket that belongs to '$boundIdentifier' were been released")
    }

    override def isClosed: Boolean = SocketLocker.state == ExternalConnectionState.CLOSED

    def read(buff: Array[Byte]): Int = read(buff, 0)

    protected def closeCurrentStreams(): Unit = {
        if (currentSocket == null)
            return
        SocketLocker.awaitRaise()

        currentSocket.close()
        currentInputStream.close()
        currentInputStream.close()
    }

    def read(length: Int): Array[Byte] = {
        val buff      = new Array[Byte](length)
        var totalRead = 0
        while (totalRead != length) {
            val bytesRead = read(buff, totalRead)
            totalRead += bytesRead
        }
        buff
    }

    def readInt(): Int = {
        NumberSerializer.deserializeInt(read(4), 0)
    }

    def read(buff: Array[Byte], pos: Int): Int = {
        SocketLocker.awaitConnected()
        ensureReady()
        try {
            val result = currentInputStream.read(buff, pos, buff.length - pos)
            if (result < 0) {
                return onDisconnect()
            }
            return result
        } catch {
            case e@(_: ConnectException | _: IOException) =>
                System.err.println(e.getMessage)
                return onDisconnect()
        }

        def onDisconnect(): Int = {
            SocketLocker.markDisconnected()
            if (isClosed || !autoReconnect)
                return -1
            reconnect()
            SocketLocker.markAsConnected()
            if (isClosed)
                return -1
            read(buff, pos)
        }

        -1
    }

    def addConnectionStateListener(@packetWorkerExecution callback: ExternalConnectionState => Unit): Unit = {
        listeners += callback
    }

    def getState: ExternalConnectionState = SocketLocker.state

    def remoteSocketAddress(): InetSocketAddress = {
        val inet = currentSocket.getInetAddress
        val port = currentSocket.getPort
        new InetSocketAddress(inet, port)
    }

    /**
     * Defines the algorithm that will handle the reconnection to the target.
     * If the method returns normally, that would mean the socket reconnection was made successfully.
     * For any exception, that make the reconnection impossible, the method may throw any exception.
     * */
    protected def handleReconnection(): Unit

    protected def markAsConnected(): Unit =
        SocketLocker.markAsConnected()

    private def ensureReady(): Unit = {
        if (currentInputStream == null || currentOutputStream == null) {
            throw new AppException("Streams are not ready")
        }

    }

    private def reconnect(): Unit = {
        AppLogger.warn(s"The connection with $boundIdentifier has been lost. Currently trying to reconnect...")
        SocketLocker.markAsConnecting()
        handleReconnection()
        AppLogger.info(s"The connection with $boundIdentifier has been reestablished.")
    }

    import ExternalConnectionState._

    private object SocketLocker {

        @volatile var isWriting                      = false
        @volatile var state: ExternalConnectionState = DISCONNECTED
        private val writeLock      = new Object
        private val disconnectLock = new Object

        def markAsWriting(): Unit = {
            isWriting = true
        }

        def unMarkAsWriting(): Unit = writeLock.synchronized {
            isWriting = false
            writeLock.notifyAll()
        }

        def awaitRaise(): Unit = writeLock.synchronized {
            if (isWriting)
                writeLock.wait()
        }

        def markDisconnected(): Unit = {
            updateState(DISCONNECTED)
        }

        def markAsConnected(): Unit = disconnectLock.synchronized {
            updateState(CONNECTED)

            disconnectLock.notifyAll() //Releases SocketLocker#awaitConnected monitor.
        }

        def markAsConnecting(): Unit = disconnectLock.synchronized {
            updateState(CONNECTING)
        }

        private def updateState(newState: ExternalConnectionState): Unit = {
            ensureReady()
            if (state == newState)
                return
            if (isClosed)
                throw new SocketClosedException("This socket is definitely closed.")

            listeners.applyAll(newState)
            state = newState
        }

        def awaitConnected(): Unit = disconnectLock.synchronized {
            if (state != CONNECTED) try {
                if (state == CLOSED)
                    throw new IllegalCloseException("Attempted to wait this socket to be connected again, but it is now closed.")

                AppLogger.warn(s"The socket is currently waiting on thread '${Thread.currentThread()}' because the connection with $boundIdentifier isn't ready or is disconnected.")
                disconnectLock.wait()
                AppLogger.trace(s"The connection with $boundIdentifier is now ready.")
            } catch {
                case _: InterruptedException =>
            }
        }

        def releaseAllMonitors(): Unit = {
            writeLock.synchronized {
                writeLock.notifyAll()
            }
            disconnectLock.synchronized {
                disconnectLock.notifyAll()
            }
        }

        def markAsClosed(): Unit = {
            updateState(CLOSED)
        }

    }

}
