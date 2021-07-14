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

package fr.linkit.engine.connection.packet.traffic.channel.request

import fr.linkit.api.connection.packet.{Packet, PacketAttributes}
import fr.linkit.api.local.system.AppLogger
import fr.linkit.engine.connection.packet.AbstractAttributesPresence
import fr.linkit.engine.connection.packet.serialization.tree.LinkLine
import fr.linkit.engine.local.utils.ScalaUtils.ensurePacketType

import java.util.NoSuchElementException
import scala.reflect.ClassTag

sealed abstract class SubmitterPacket(@LinkLine id: Int, @LinkLine packets: Array[Packet]) extends AbstractAttributesPresence with Packet {

    @transient private var packetIndex                  = 0
    @transient private var attributes: PacketAttributes = _

    @throws[NoSuchElementException]("If this method is called more times than packet array's length" + this)
    def nextPacket[P <: Packet : ClassTag]: P = {
        AppLogger.vDebug(s"packetIndex: $packetIndex, packets: ${packets.mkString("Array(", ", ", ")")} + $hashCode")
//        Thread.dumpStack()
        if (packetIndex >= packets.length)
            throw new NoSuchElementException("toString")

        val packet = packets(packetIndex)
        packetIndex += 1
        ensurePacketType[P](packet)
    }

    def foreach(action: Packet => Unit): Unit = {
        packets.foreach(action)
    }

    def getAttributes: PacketAttributes = attributes

    def getAttribute[S](key: Serializable): Option[S] = attributes.getAttribute(key)

    def putAttribute(key: Serializable, value: Serializable): this.type = {
        attributes.putAttribute(key, value)
        this
    }

    private[packet] def setAttributes(attributes: PacketAttributes): Unit = {
        if (this.attributes != null && this.attributes.ne(attributes))
            throw new IllegalStateException("Attributes already set !")
        AppLogger.vDebug(s"SETTING ATTRIBUTES FOR SUBMITTER PACKET $this : $attributes")
        this.attributes = attributes
    }

    override def toString: String = s"${getClass.getSimpleName}(id: $id, packets: ${packets.mkString("Array(", ", ", ")")}, attr: $attributes)"

}

case class ResponsePacket(id: Int, packets: Array[Packet])
        extends SubmitterPacket(id, packets) {

}

case class RequestPacket(id: Int, packets: Array[Packet])
        extends SubmitterPacket(id, packets)
