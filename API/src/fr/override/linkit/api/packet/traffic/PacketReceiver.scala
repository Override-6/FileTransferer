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

package fr.`override`.linkit.api.packet.traffic

import fr.`override`.linkit.api.packet.{DedicatedPacketCoordinates, Packet}

trait PacketAsyncReceiver extends PacketChannel {
    def addOnPacketReceived(callback: (Packet, DedicatedPacketCoordinates) => Unit): Unit
}

trait PacketSyncReceiver extends PacketChannel {

    def nextPacket[P <: Packet]: P

    /**
     * @return true if this channel contains stored packets. In other words, return true if [[nextPacket]] will not wait
     * */
    def haveMorePackets: Boolean
}