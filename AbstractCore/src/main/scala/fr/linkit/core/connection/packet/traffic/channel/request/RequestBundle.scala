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

package fr.linkit.core.connection.packet.traffic.channel.request

import fr.linkit.api.connection.packet.channel.PacketChannel
import fr.linkit.api.connection.packet.{DedicatedPacketCoordinates, PacketAttributes}
import fr.linkit.core.connection.packet.AbstractBundle

case class RequestBundle(channel: PacketChannel,
                         override val packet: RequestPacket,
                         override val coords: DedicatedPacketCoordinates,
                         responseSubmitter: ResponseSubmitter) extends AbstractBundle(channel) {

    override val attributes: PacketAttributes = packet.getAttributes
}
