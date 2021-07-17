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

package fr.linkit.server.connection.packet

import fr.linkit.api.connection.NoSuchConnectionException
import fr.linkit.api.connection.packet.traffic.{PacketTraffic, PacketWriter}
import fr.linkit.api.connection.packet.{DedicatedPacketCoordinates, Packet, PacketAttributes}
import fr.linkit.engine.connection.packet.SimplePacketAttributes
import fr.linkit.engine.connection.packet.traffic.WriterInfo
import fr.linkit.server.connection.ServerConnection

class ServerPacketWriter(serverConnection: ServerConnection, info: WriterInfo) extends PacketWriter {

    override val identifier       : Int           = info.identifier
    override val traffic          : PacketTraffic = info.traffic
    override val serverIdentifier : String        = serverConnection.currentIdentifier
    override val currentIdentifier: String        = traffic.currentIdentifier

    //private val notifier = info.notifier
    //private val hooks = info.packetHooks

    override def writePacket(packet: Packet, targetIDs: String*): Unit = {
        writePacket(packet, SimplePacketAttributes.empty, targetIDs: _*)
    }

    override def writePacket(packet: Packet, attributes: PacketAttributes, targetIDs: String*): Unit = {
        targetIDs.foreach(targetID => {
            /*
             * If the targetID is the same as the server's identifier, that means that we target ourself,
             * so the packet, as it can't be written to a socket that target the current server, will be directly
             * injected into the traffic.
             * */
            if (targetID == serverIdentifier) {
                val coords = DedicatedPacketCoordinates(identifier, targetID, serverIdentifier)
                traffic.processInjection(packet, attributes, coords)
                return
            }
            val opt = serverConnection.getConnection(targetID)
            if (opt.isDefined) {
                opt.get.sendPacket(packet, attributes, identifier)
            } else {
                throw NoSuchConnectionException(s"Attempted to send a packet to target $targetID, but this conection is missing or not connected.")
            }
        })
    }

    override def writeBroadcastPacket(packet: Packet, discardedIDs: String*): Unit = {
        writeBroadcastPacket(packet, SimplePacketAttributes.empty, discardedIDs: _*)
    }

    override def writeBroadcastPacket(packet: Packet, attributes: PacketAttributes, discarded: String*): Unit = {
        serverConnection.broadcastPacket(packet, attributes, currentIdentifier, identifier, discarded: _*)
    }
}
