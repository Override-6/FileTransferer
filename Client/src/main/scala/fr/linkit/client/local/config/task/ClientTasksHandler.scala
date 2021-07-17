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

package fr.linkit.client.local.config.task

import fr.linkit.api.connection.ConnectionContext
import fr.linkit.api.connection.packet.{DedicatedPacketCoordinates, Packet}
import fr.linkit.api.connection.task.{TaskException, TaskExecutor, TasksHandler}
import fr.linkit.api.local.system.{AppLogger, Reason}
import fr.linkit.engine.connection.task.{SimpleCompleterHandler, TaskTicket}
import fr.linkit.engine.local.system.{SystemOrder, SystemPacketChannel}

import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}
import scala.util.control.NonFatal

protected class ClientTasksHandler(private val systemChannel: SystemPacketChannel,
                                   private val connection: ConnectionContext) extends TasksHandler {

    private val queue      : BlockingQueue[TaskTicket] = new ArrayBlockingQueue[TaskTicket](1000)
    private var tasksThread: Thread                    = _

    @volatile private var currentTicket: TaskTicket = _
    @volatile private var open                      = false

    override val tasksCompleterHandler = new SimpleCompleterHandler()
    override val identifier: String    = connection.currentIdentifier

    override def schedule(executor: TaskExecutor, taskIdentifier: Int, targetID: String, ownFreeWill: Boolean): Unit = {
        if (targetID == identifier)
            throw new TaskException("Can't start a task with oneself !")

        val ticket = new TaskTicket(executor, taskIdentifier, connection, targetID, ownFreeWill)
        queue.offer(ticket)
    }

    override def handlePacket(packet: Packet, coordinates: DedicatedPacketCoordinates): Unit = {
        try {
            tasksCompleterHandler.handleCompleter(packet, coordinates, this)
        } catch {
            case e: TaskException =>
                Console.err.println(e.getMessage)
                systemChannel.sendOrder(SystemOrder.ABORT_TASK, Reason.INTERNAL_ERROR)
        }
    }

    override def close(): Unit = {
        if (!open)
            throw new IllegalStateException("ClientTasksHandler is already closed.")
        if (currentTicket != null) {
            currentTicket.abort()
            currentTicket = null
        }
        open = false
        if (tasksThread != null)
            tasksThread.interrupt()
    }

    override def skipCurrent(reason: Reason): Unit = {
        //Restarting the thread causes the current task to be skipped
        //And wait or execute the task that come after it
        close()
        start()
    }

    def start(): Unit = {
        tasksThread = new Thread(() => listen())
        tasksThread.setName("Client Tasks scheduler")
        tasksThread.start()
    }

    private def listen(): Unit = {
        open = true
        while (open)
            executeNextTask()
    }

    private def executeNextTask(): Unit = {
        try {
            val ticket = queue.take()
            if (!open) return
            currentTicket = ticket
            ticket.start()
        } catch {
            //Do not considerate InterruptedException
            case _: InterruptedException =>
            case NonFatal(e) => AppLogger.printStackTrace(e)
        }
    }

}
