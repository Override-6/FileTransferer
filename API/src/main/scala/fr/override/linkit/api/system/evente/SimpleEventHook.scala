package fr.`override`.linkit.api.system.evente

import fr.`override`.linkit.api.concurrency.RelayThreadPool
import fr.`override`.linkit.api.system.evente.EventHook
import fr.`override`.linkit.api.utils.ConsumerContainer
import org.jetbrains.annotations.NotNull

class SimpleEventHook[L <: EventListener, E <: Event[_, L]](listenerMethods: ((L, E) => Unit)*) extends EventHook[L, E] {
    private val consumers = ConsumerContainer[E]()

    override def await(@NotNull lock: AnyRef = new Object): Unit = {
        var stillBusy = true
        addOnce {
            lock.synchronized {
                stillBusy = false
                lock.notifyAll()
            }
        }
        RelayThreadPool.executeRemainingTasks(lock, stillBusy)
    }

    override def add(action: E => Unit): Unit = consumers += action

    override def addOnce(action: E => Unit): Unit = consumers +:+= action

    override def add(action: => Unit): Unit = add(_ => action)

    override def addOnce(action: => Unit): Unit = addOnce(_ => action)

    override def executeEvent(event: E, listeners: Seq[L]): Unit = {
        listeners.foreach(listener => listenerMethods.foreach(_ (listener, event)))
        consumers.applyAll(event)
    }
}

object SimpleEventHook {
    def apply[L <: EventListener, E <: Event[_, L]](methods: (L, E) => Unit*): SimpleEventHook[L, E] = {
        new SimpleEventHook[L, E](methods: _*)
    }
}