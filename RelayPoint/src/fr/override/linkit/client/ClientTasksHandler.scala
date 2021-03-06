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

package fr.`override`.linkit.client

import fr.`override`.linkit.api.exception.TaskException
import fr.`override`.linkit.api.packet.DedicatedPacketCoordinates
import fr.`override`.linkit.api.packet.fundamental.TaskInitPacket
import fr.`override`.linkit.api.system.{CloseReason, SystemOrder, SystemPacketChannel}
import fr.`override`.linkit.api.task.{TaskCompleterHandler, TaskExecutor, TaskTicket, TasksHandler}

import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}
import scala.util.control.NonFatal

protected class ClientTasksHandler(private val systemChannel: SystemPacketChannel,
                                   private val relay: RelayPoint) extends TasksHandler {

    private val queue: BlockingQueue[TaskTicket] = new ArrayBlockingQueue[TaskTicket](relay.configuration.taskQueueSize)
    private var tasksThread: Thread = _

    @volatile private var currentTicket: TaskTicket = _
    @volatile private var open = false

    override val tasksCompleterHandler = new TaskCompleterHandler()
    override val identifier: String = relay.identifier

    override def schedule(executor: TaskExecutor, taskIdentifier: Int, targetID: String, ownFreeWill: Boolean): Unit = {
        if (targetID == identifier)
            throw new TaskException("Can't start a task with oneself !")

        val ticket = new TaskTicket(executor, relay, taskIdentifier, targetID, ownFreeWill)
        queue.offer(ticket)
    }

    override def handlePacket(packet: TaskInitPacket, coordinates: DedicatedPacketCoordinates): Unit = {
        try {
            tasksCompleterHandler.handleCompleter(packet, coordinates, this)
        } catch {
            case e: TaskException =>
                Console.err.println(e.getMessage)
                systemChannel.sendOrder(SystemOrder.ABORT_TASK, CloseReason.INTERNAL_ERROR)

                val errConsole = relay.getConsoleErr(coordinates.senderID)
                errConsole.print(e)
        }
    }


    override def close(reason: CloseReason): Unit = {
        if (currentTicket != null) {
            currentTicket.abort(reason)
            currentTicket = null
        }
        open = false
        if (tasksThread != null)
            tasksThread.interrupt()
    }

    override def skipCurrent(reason: CloseReason): Unit = {
        //Restarting the thread causes the current task to be skipped
        //And wait or execute the task that come after it
        close(reason)
        start()
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
            case NonFatal(e) => e.printStackTrace()
        }
    }

    def start(): Unit = {
        tasksThread = new Thread(() => listen())
        tasksThread.setName("Client Tasks scheduler")
        tasksThread.start()
    }

    override def isClosed: Boolean = !open
}
