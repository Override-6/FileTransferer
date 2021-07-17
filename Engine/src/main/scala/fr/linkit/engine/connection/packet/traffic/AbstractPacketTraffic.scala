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

import fr.linkit.api.connection.packet.channel.ChannelScope
import fr.linkit.api.connection.packet.channel.ChannelScope.ScopeFactory
import fr.linkit.api.connection.packet.traffic._
import fr.linkit.api.connection.packet.traffic.injection.{PacketInjection, PacketInjectionController}
import fr.linkit.api.connection.packet.{DedicatedPacketCoordinates, Packet, PacketAttributes}
import fr.linkit.api.local.concurrency.Procrastinator
import fr.linkit.api.local.system.{AppLogger, ClosedException, JustifiedCloseable, Reason}
import fr.linkit.engine.connection.packet.traffic.injection.ParallelInjectionContainer
import fr.linkit.engine.local.concurrency.PacketReaderThread
import fr.linkit.api.local.concurrency.WorkerPools.currentTasksId

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect.{ClassTag, classTag}
import scala.util.control.NonFatal

abstract class AbstractPacketTraffic(override val currentIdentifier: String, procrastinator: Procrastinator) extends PacketTraffic {

    private  val holders            = mutable.Map.empty[Int, ScopesHolder]
    private  val lostInjections     = mutable.Map.empty[Int, ListBuffer[PacketInjection]]
    override val injectionContainer = new ParallelInjectionContainer(currentIdentifier)
    @volatile private var closed    = false

    override def getInjectable[C <: PacketInjectable : ClassTag](id: Int,
                                                                 scopeFactory: ScopeFactory[_ <: ChannelScope],
                                                                 factory: PacketInjectableFactory[C]): C = {
        val scope     = scopeFactory(newWriter(id))
        val holderOpt = holders.get(id)
        if (holderOpt.isDefined) {
            val holder  = holderOpt.get
            val attempt = holder.tryRetrieveInjectable(scope)
            if (attempt.isDefined) {
                return attempt.get
            }

            if (holder.canConflict(scope)) {
                throw new ConflictException("This scope can conflict with other scopes that are registered within this injectable identifier")
            }
        }

        completeCreation(scope, factory)
    }

    override def canConflict(identifier: Int, scope: ChannelScope): Boolean = {
        holders
                .get(identifier)
                .exists(_.canConflict(scope))
    }

    private def completeCreation[C <: PacketInjectable](scope: ChannelScope, factory: PacketInjectableFactory[C]): C = {
        val channel = factory.createNew(null, scope)
        register(scope, channel)
        channel
    }

    private def register(scope: ChannelScope, dedicated: PacketInjectable): Unit = {
        ensureOpen()

        val id = dedicated.identifier
        init(dedicated)

        holders.getOrElseUpdate(id, ScopesHolder(id)).register(scope, dedicated)
    }

    private def init(injectable: PacketInjectable): Unit = {
        val id = injectable.identifier

        //Will inject every lost packets
        val opt = lostInjections.get(id)
        if (opt.isDefined) lostInjections.synchronized {
            opt.get.foreach(injectable.inject)
            lostInjections.remove(id)
        }
    }

    private def getInjectables(identifier: Int, target: String): Iterable[PacketInjectable] = {
        val opt = holders.get(identifier)

        if (opt.isEmpty)
            return Iterable()

        opt.get.getInjectables(target)
    }

    override def close(reason: Reason): Unit = {
        holders.values
                .foreach(_.close(reason))
        holders.clear()
        closed = true
    }

    override def isClosed: Boolean = closed

    protected def ensureOpen(): Unit = {
        if (closed)
            throw new ClosedException("This Traffic handler is closed")
    }

    override def processInjection(packet: Packet, attr: PacketAttributes, coordinates: DedicatedPacketCoordinates): Unit = {
        processInjection(injectionContainer.makeInjection(packet, attr, coordinates))
    }

    override def processInjection(injection: PacketInjectionController): Unit = procrastinator.runLater {
        if (injection.isProcessing) {
            AppLogger.vError(s"$currentTasksId <> Current thread has been discarded from injection because this injection is already processed.")
            injection.processRemainingPins()
        }
        else injection.process {

            AppLogger.vError(s"$currentTasksId <> PROCESSING INJECTION '${injection.coordinates}' - ${injection.hashCode()}")
            val coordinates = injection.coordinates
            PacketReaderThread.checkNotCurrent()
            ensureOpen()

            val id = coordinates.injectableID

            val sender      = coordinates.senderID
            val injectables = getInjectables(id, sender)
            if (injectables.isEmpty) lostInjections.synchronized {
                lostInjections.getOrElseUpdate(id, ListBuffer.empty) += injection
            } else {
                performInjection(injection, injectables)
            }
        }
    }

    private def performInjection(injection: PacketInjectionController, injectables: Iterable[PacketInjectable]): Unit = {
        AppLogger.vError(s"$currentTasksId <> PERFORMING PIN ATTACHMENT '${injection.coordinates}' - ${injection.hashCode()}")
        injection.performPinAttach(injectables)
        AppLogger.vError(s"$currentTasksId <> PERFORMING PIN INJECTIONS '${injection.coordinates}' - ${injection.hashCode()}")
        injection.processRemainingPins()

        procrastinator.runLater {
            AppLogger.vError(s"$currentTasksId <> REMOVING INJECTION '${injection.coordinates}' - ${injection.hashCode()}")
            injectionContainer.removeInjection(injection)
            AppLogger.vError(s"$currentTasksId <> REMOVED INJECTION '${injection.coordinates}' - ${injection.hashCode()}")
        }
    }

    protected case class ScopesHolder(identifier: Int) extends JustifiedCloseable {

        private val cache  = mutable.Set.empty[(ChannelScope, PacketInjectable)]
        private var closed = false

        override def close(reason: Reason): Unit = {
            for (tuple <- cache if tuple._2.isOpen) try {
                tuple._2.close()
            } catch {
                case NonFatal(e) => AppLogger.printStackTrace(e)
            }
            cache.clear()
            closed = true
        }

        override def isClosed: Boolean = closed

        def canConflict(scope: ChannelScope): Boolean = {
            cache.exists(_._1.canConflictWith(scope))
        }

        def isAuthorised(targetID: String): Boolean = {
            cache.exists(_._1.areAuthorised(targetID))
        }

        def getInjectables(target: String): Seq[PacketInjectable] = {
            cache
                    .filter(_._1.areAuthorised(target))
                    .map(_._2)
                    .toSeq
        }

        def tryRetrieveInjectable[I <: PacketInjectable : ClassTag](scope: ChannelScope): Option[I] = {
            val injectableClass = classTag[I].runtimeClass
            cache.find(tuple => tuple._1 == scope && tuple._2.getClass == injectableClass)
                    .map(_._2)
                    .asInstanceOf[Option[I]]
        }

        def register(scope: ChannelScope, injectable: PacketInjectable): Unit = {
            cache += ((scope, injectable))
        }
    }

}