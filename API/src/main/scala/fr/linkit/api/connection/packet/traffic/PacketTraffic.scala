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

package fr.linkit.api.connection.packet.traffic

import fr.linkit.api.connection.packet.channel.ChannelScope
import fr.linkit.api.connection.packet.traffic.injection.PacketInjectionController
import fr.linkit.api.connection.packet.{DedicatedPacketCoordinates, Packet, PacketAttributes}
import fr.linkit.api.local.concurrency.workerExecution
import fr.linkit.api.local.system.JustifiedCloseable

trait PacketTraffic extends JustifiedCloseable with PacketInjectableContainer {

    val currentIdentifier: String

    val serverIdentifier: String

    val injectionContainer: InjectionContainer

    @workerExecution
    def processInjection(packet: Packet, attr: PacketAttributes, coordinates: DedicatedPacketCoordinates): Unit

    @workerExecution
    def processInjection(injection: PacketInjectionController): Unit

    def canConflict(id: Int, scope: ChannelScope): Boolean

    def newWriter(id: Int): PacketWriter

}

object PacketTraffic {

    val SystemChannelID = 1
    val RemoteConsoles  = 2
}