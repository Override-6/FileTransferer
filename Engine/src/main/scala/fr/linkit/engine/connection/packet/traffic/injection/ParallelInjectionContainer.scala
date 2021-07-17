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

package fr.linkit.engine.connection.packet.traffic.injection

import fr.linkit.api.connection.packet._
import fr.linkit.api.connection.packet.traffic.InjectionContainer
import fr.linkit.api.connection.packet.traffic.injection.{PacketInjection, PacketInjectionController}
import fr.linkit.api.local.system.AppLogger
import fr.linkit.api.local.concurrency.WorkerPools.currentTasksId

import scala.collection.mutable

class ParallelInjectionContainer(currentIdentifier: String) extends InjectionContainer {

    private val processingInjections = new mutable.LinkedHashMap[(Int, String), ParallelInjection]

    override def makeInjection(bundle: Bundle): PacketInjectionController = {
        val dedicated = bundle.coords match {
            case dedicated: DedicatedPacketCoordinates => dedicated
            case broadcast: BroadcastPacketCoordinates => broadcast.getDedicated(currentIdentifier)
            case _                                     => throw new UnsupportedOperationException("Attempted to perform an injection with unknown PacketCoordinates implementation.")
        }
        makeInjection(bundle.packet, bundle.attributes, dedicated)
    }

    override def makeInjection(packet: Packet,
                               attributes: PacketAttributes,
                               coordinates: DedicatedPacketCoordinates): PacketInjectionController = this.synchronized {
        val number = packet.number
        AppLogger.vDebug(s"${currentTasksId} <> $number -> CREATING INJECTION FOR PACKET $packet WITH COORDINATES $coordinates AND ATTRIBUTES $attributes")
        val id     = coordinates.injectableID
        val sender = coordinates.senderID

        val injection = processingInjections.get((id, sender)) match {
            case Some(value) =>
                AppLogger.vError(s"${currentTasksId} <> $number -> INJECTION ALREADY EXISTS, ADDING PACKET.")
                value
            case None        =>
                AppLogger.vError(s"${currentTasksId} <> $number -> INJECTION DOES NOT EXISTS, CREATING IT.")
                val injection = new ParallelInjection(coordinates)
                processingInjections.put((id, sender), injection)
                injection
        }

        injection.insert(packet, attributes)
        injection
    }

    override def isInjecting(injection: PacketInjection): Boolean = {
        val coords = injection.coordinates
        val id     = coords.injectableID
        val sender = coords.senderID
        processingInjections.contains((id, sender))
    }

    def removeInjection(injection: PacketInjectionController): Unit = this.synchronized {
        val coords = injection.coordinates
        val id     = coords.injectableID
        val sender = coords.senderID
        processingInjections.remove((id, sender))
    }

}

