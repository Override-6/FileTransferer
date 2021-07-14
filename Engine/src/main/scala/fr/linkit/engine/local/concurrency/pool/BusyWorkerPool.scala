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

package fr.linkit.engine.local.concurrency.pool

import fr.linkit.api.local.concurrency._
import fr.linkit.api.local.system.AppLogger
import fr.linkit.engine.local.concurrency.{SimpleAsyncTask, now, timedPark}

import java.util.concurrent._
import java.util.concurrent.locks.LockSupport
import scala.collection.mutable.ListBuffer
import scala.util.Try
import scala.util.control.NonFatal

/**
 * This class handles a FixedThreadPool from Java executors, excepted that the used threads can be busy about
 * executing other tasks contained in the pool instead of stupidly waiting in an object monitor
 * <h2> Problem </h2>
 * <p>
 * Let's say that we have a pool of 3 threads that handle deserialization and packet injections. <br>
 * (see [[fr.linkit.api.connection.packet.traffic.PacketInjectable]] for further details about injection
 * Then suddenly, for a reason that can often appear, every threads of the pool are waiting to receipt another packet.<br>
 * The waited packet will effectively be downloaded by the [[PacketReaderThread]],
 * but it could not be deserialized and injected because all the thread are currently waiting for an injection.<br>
 * This way, a kind of deadlock will occur because each threads are waiting for their packet to be injected,
 * and there is no free thread that would process the packets in order to notify other threads that their packet has been effectively received.<br>
 * </p>
 *
 * <h3> Pseudo code : </h3>
 *
 * Here is an illustration of a normal execution, where a thread is waiting for a packet to be received,
 * and where a second thread is injecting the packet and notifying the first thread that a packet has been received.
 *
 * <u>Thread 1 : waiting for a packet to be received</p>
 * {{{
 *      val channel = relay.getInjectable(x, ChannelScope.y, SyncPacketChannel)
 *      val nextPacket = channel.nextPacket()
 *      //println("A new packet has been received !")
 *      // Process nextPacket....
 * }}}
 *
 * <u>Thread 2 : injecting the next packet that concerns the channel, handled by the thread 1.</u><br>
 * {{{
 *      val injectable = //Retrieves the needed injectable, stored with 'x' identifier
 *      val packetInjection = //Get the concerned injection object
 *      injectable.injectPacket(packetInjection) //The injection will notify the first thread.
 *      //println("Another packet injection has been performed !")
 * }}}
 *
 * <h2> Solution </h2>
 * <p>
 * In a normal execution, where a second thread is free to notify the first thread,
 * the two prints would be done at same time
 * </p>
 * <p>
 * Therefore, if the second thread were not able to handle the injection, because it would be busy to
 * execute another task submitted to thread pool, the first thread could not be
 * notified, and will wait until a thread is free to process the injection.<br>
 * But we have to rely on the fact that the first thread is doing noting.<br>
 * So, we have a thread that is waiting for a packet to be provided, and will do
 * absolutely nothing until he does not received its wants,
 * where he can take the time he is sleeping for executing other
 * tasks in the pool ? What a lazy thread !
 * </p>
 * <p>
 * The Busy thread system will save this lost time in order to fluidify task execution,
 * and make one thread able to handle multiple tasks when a task need to wait something.
 *
 * @see [[BusyBlockingQueue]] for busy waitings example.
 * @param initialThreadCount The number of threads the pool will contain (can ba changed with [[setThreadCount]].
 * */
class BusyWorkerPool(initialThreadCount: Int, val name: String) extends WorkerPool with AutoCloseable {

    import fr.linkit.api.local.concurrency.WorkerPools._

    if (initialThreadCount <= 0)
        throw new IllegalArgumentException(s"Worker pool '$name' must contain at least 1 thread, provided: '$initialThreadCount'")

    private val workQueue = new LinkedBlockingQueue[Runnable]()
    //private val choreographer = new Choreographer(this)
    private val executor  = new ThreadPoolExecutor(initialThreadCount, initialThreadCount, 0, TimeUnit.MILLISECONDS, workQueue, getThreadFactory)

    //The extracted workQueue of the executor which contains all the tasks to execute
    private val workers = ListBuffer.empty[WorkerThread]
    private var closed  = false

    //additional values for debugging
    @volatile private var activeThreads = 0
    @volatile private var taskCount     = 0
    private val workTaskIds             = ListBuffer.empty[Int]

    override def close(): Unit = {
        closed = true
        executor.shutdownNow()
    }

    /**
     * Submits a task to the executor thread pool.
     * The task will then be handled directly or right after a thread is looking for other task to schedule.
     *
     * @throws IllegalStateException if the pool is closed
     * @param task the task to execute in the thread pool
     * */
    override def runLaterControl[A](@workerExecution task: => A): AsyncTask[A] = {
        if (closed)
            throw new IllegalStateException("Attempted to submit a task in a closed thread pool !")

        var runnable: Runnable = null
        taskCount += 1
        val submittedTaskID = taskCount
        val currentTask     = currentTaskWithController.orNull
        val childTask       = SimpleAsyncTask[A](submittedTaskID, currentTask)(Try(task))
        runnable = () => {
            val rootExecution = currentTaskExecutionDepth == 0
            if (rootExecution)
                activeThreads += 1
            AppLogger.vWarn(s"${currentTasksId} <> ($activeThreads / ${threadCount}) TASK $submittedTaskID TAKEN FROM POOL $name")

            try {
                currentWorker
                        .getController
                        .runTask(childTask)
            } catch {
                case NonFatal(e) =>
                    e.printStackTrace()
            }
            if (rootExecution)
                activeThreads -= 1
            workTaskIds.synchronized {
                workTaskIds -= submittedTaskID
                val tasks = workTaskIds.toString()
                AppLogger.vWarn(s"${currentTasksId} <> ($activeThreads / ${threadCount}) TASK ACCOMPLISHED ($submittedTaskID) ($tasks).")
            }
        }
        val tasks = workTaskIds.synchronized {
            workTaskIds += submittedTaskID
            workTaskIds.toString()
        }
        AppLogger.vWarn(s"${currentTasksId} <> ($activeThreads / ${threadCount}) TASK $submittedTaskID SUBMIT TO POOL $name, TOTAL TASKS : $tasks (${System.identityHashCode(workQueue)}), $this")
        executor.submit(runnable)

        //If there is one busy thread that is waiting for a new task to be performed,
        //It would instantly execute the current task.
        unparkBusyThread()
        childTask
    }

    override def ensureCurrentThreadOwned(): Unit = {
        ensureCurrentThreadOwned(s"Current thread is not owned by worker pool '$name'")
    }

    override def ensureCurrentThreadOwned(msg: String): Unit = {
        if (!isCurrentThreadOwned)
            throw IllegalThreadException(msg)
    }

    override def isCurrentThreadOwned: Boolean = {
        currentPool.exists(_ eq this)
    }

    override def execute(runnable: Runnable): Unit = {
        if (isCurrentThreadOwned) {
            runnable.run()
            return
        }
        runLaterControl(runnable.run())
    }

    override def reportFailure(cause: Throwable): Unit = {
        if (!isCurrentThreadWorker)
            return
        currentWorker
                .getController
                .getCurrentTask
                .get
                .notifyNestThrow(cause)
    }

    def sleepingThreads: Iterable[WorkerThread] = {
        workers.filter(_.isSleeping)
    }

    def countRemainingTasks: Int = workQueue.size()

    /**
     * @return the number of threads that are currently executing a task.
     * */
    def busyThreads: Int = activeThreads

    def setThreadCount(newCount: Int): Unit = {
        executor.setMaximumPoolSize(newCount)
        AppLogger.trace(s"$name's core pool size is set to $newCount")
        executor.setCorePoolSize(newCount)
    }

    /**
     * Keep executing tasks contained in the workQueue while
     * the current task needs to wait
     *
     * in plain language, this method will make the thread execute tasks
     * as long as it is not stopped by an auxiliary thread.
     * However, if the thread can't process other tasks, and still not stopped, it will wait until a task get submitted.
     *
     * @throws IllegalThreadException if the current thread is not a [[WorkerThread]]
     * */
    @workerExecution
    override def pauseCurrentTask(): Unit = {
        val worker      = currentWorker.getController
        val currentTask = worker.getCurrentTask.get
        AppLogger.vError(s"$currentTasksId current task is about to pause indefinitely...")
        currentTask.setPaused()
        executeRemainingTasks()

        worker.execWhileCurrentTaskPaused(LockSupport.park(currentTask), currentTask.isPaused) { _ =>
            executeRemainingTasks()
        }
        currentTask.setContinue()
        AppLogger.vError(s"$currentTasksId task '${currentTask.taskID}' is continuing")
    }

    /**
     * Keep the current thread busy with task execution for at least
     * x milliseconds.
     *
     * @param timeoutMillis the number of milliseconds the thread must be busy.
     * @throws IllegalThreadException if the current thread is not a [[WorkerThread]]
     * */
    override def pauseCurrentTaskForAtLeast(timeoutMillis: Long): Unit = {
        ensureCurrentThreadOwned()
        val worker      = currentWorker.getController
        val currentTask = worker.getCurrentTask.get
        AppLogger.vError(s"$currentTasksId current task is about to pause for at least $timeoutMillis ms...")

        if (timeoutMillis == 0) {
            pauseCurrentTask()
            return
        }
        if (timeoutMillis <= 0)
            throw new IllegalArgumentException("timeoutMillis is negative.")

        def pauseCurrentTaskTimed(): Long = {
            var busiedMillis: Long = 0
            while (!workQueue.isEmpty && busiedMillis <= timeoutMillis) {
                val t0 = now()
                workQueue.take().run()
                val t1 = now()
                busiedMillis += (t1 - t0)
            }
            busiedMillis
        }

        currentTask.setPaused()

        var toWait = timeoutMillis - pauseCurrentTaskTimed()
        worker.execWhileCurrentTaskPaused(timedPark(currentTask, toWait), toWait > 0) { waited =>
            toWait -= waited
            if (toWait <= 0) {
                currentTask.setContinue()
            } else toWait -= pauseCurrentTaskTimed()
        }

        currentTask.setContinue()
        AppLogger.vError(s"${currentTask.taskID} is continuing...")
    }

    def ensureCurrentThreadNotOwned(): Unit = {
        ensureCurrentThreadNotOwned(s"Current thread is owned by worker pool '$name'")
    }

    def ensureCurrentThreadNotOwned(msg: String): Unit = {
        if (isCurrentThreadOwned)
            throw IllegalThreadException(msg)
    }

    /**
     * Creates a blocking queue that keep busy its thread instead of make it waiting
     * the provided queue will use the busy threading system for concurrent operations such as
     * [[BlockingQueue#take()]]
     *
     * @tparam A the type of element the queue will contains
     * @return a [[BusyBlockingQueue]]
     */
    override def newBusyQueue[A]: BlockingQueue[A] = {
        new BusyBlockingQueue[A](this)
    }

    /**
     * The Task Execution Depth is an int value that determines the number of tasks
     * a thread is consequently executing.
     *
     * @return the task execution depth of the current thread
     * @throws IllegalThreadException if the current thread is not a [[WorkerThread]]
     * */
    @workerExecution
    def currentTaskExecutionDepth: Int = {
        ensureCurrentThreadOwned(s"This action is only permitted to relay threads of thread pool $name")
        currentWorker.taskRecursionDepth
    }

    private def unparkBusyThread(): Unit = workers.synchronized {
        val sleepingWorker = workers.find(_.isSleeping)
        AppLogger.vDebug(s"unparking busy thread ${sleepingWorker.orNull} (if null, no thread is sleeping)")
        sleepingWorker match {
            case Some(worker) => LockSupport.unpark(worker)
            case None         => //no-op
        }
    }

    private def threadCount: Int = workers.length

    private def getThreadFactory: ThreadFactory = target => {
        val worker = new BusyWorkerThread(target, this, threadCount + 1)
        workers.synchronized {
            workers += worker
        }
        worker
    }

    /**
     * Executes all tasks contained in the workQueue
     * until the queue is empty.
     *
     * @throws IllegalThreadException if the current thread is not a [[WorkerThread]]
     * */
    private def executeRemainingTasks(): Unit = {
        ensureCurrentThreadOwned()

        //AppLogger.vDebug(s"EXECUTING ALL REMAINING TASKS (${System.identityHashCode(workQueue)}), $this")
        val currentController = currentWorker.getController
        while (!workQueue.isEmpty && currentTask.isPaused) {
            val task = workQueue.poll()
            if (task != null) {
                currentController.runSubTask(task)
            }
        }
        AppLogger.vError(s"Exit executeRemainingTasks... (${workQueue.isEmpty}, ${currentTask.isPaused})")
    }

    override def runLater(task: => Unit): Unit = runLaterControl {
        try {
            task
        } catch {
            case NonFatal(e)  =>
                e.printStackTrace()
            case e: Throwable =>
                e.printStackTrace()
                throw e
        }
    }
}

