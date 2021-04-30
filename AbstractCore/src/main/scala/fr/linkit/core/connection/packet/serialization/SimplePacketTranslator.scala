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

package fr.linkit.core.connection.packet.serialization

import fr.linkit.api.connection.network.Network
import fr.linkit.api.connection.packet.serialization._
import fr.linkit.api.connection.packet.{Packet, PacketAttributes, PacketCoordinates}

class SimplePacketTranslator extends PacketTranslator {

    private val serializer = new DefaultSerializer()

    override def translate(packetInfo: TransferInfo): PacketSerializationResult = {
        new LazyPacketSerializationResult(packetInfo, () => serializer)
    }

    //TODO Create trait named "PacketDeserializationResult" even if it is empty.
    override def translate(bytes: Array[Byte]): PacketTransferResult = {
        new LazyPacketDeserializationResult(bytes, () => serializer)
    }

    override def translateCoords(coords: PacketCoordinates, target: String): Array[Byte] = {
        serializer.serialize(coords, false)
    }

    override def translateAttributes(attribute: PacketAttributes, target: String): Array[Byte] = {
        serializer.serialize(attribute, false)
    }

    override def translatePacket(packet: Packet, target: String): Array[Byte] = {
        serializer.serialize(packet, false)
    }

    override def getSerializer: Serializer = serializer

    override def initNetwork(network: Network): Unit = serializer.initNetwork(network)

    override val signature: Array[Byte] = serializer.signature
}
