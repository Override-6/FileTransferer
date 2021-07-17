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
import fr.linkit.api.connection.packet.traffic.PacketWriter
import fr.linkit.api.connection.packet.{Packet, PacketAttributes}
import fr.linkit.engine.connection.packet.{AbstractAttributesPresence, SimplePacketAttributes}

object ChannelScopes {

    final case class BroadcastScope private(override val writer: PacketWriter, discarded: String*)
            extends AbstractAttributesPresence with ChannelScope {

        override def sendToAll(packet: Packet, attributes: PacketAttributes): Unit = {
            defaultAttributes.drainAttributes(attributes)
            writer.writeBroadcastPacket(packet, attributes, discarded: _*)
        }

        override def sendTo(packet: Packet, attributes: PacketAttributes, targetIDs: String*): Unit = {
            defaultAttributes.drainAttributes(attributes)
            writer.writePacket(packet, attributes, targetIDs: _*)
        }

        override def sendToAll(packet: Packet): Unit = sendToAll(packet, SimplePacketAttributes.empty)

        override def sendTo(packet: Packet, targetIDs: String*): Unit = sendTo(packet, SimplePacketAttributes.empty, targetIDs: _*)

        override def areAuthorised(identifiers: String*): Boolean = true //everyone is authorised in a BroadcastScope

        override def canConflictWith(scope: ChannelScope): Boolean = {
            //As Long As everyone is authorised by a BroadcastScope,
            //the other scope wouldn't conflict with this scope only if it discards all identifiers.
            scope.isInstanceOf[BroadcastScope] || scope.canConflictWith(this)
        }

        override def equals(obj: Any): Boolean = {
            obj.isInstanceOf[BroadcastScope]
        }

        override def shareWriter[S <: ChannelScope](factory: ScopeFactory[S]): S = factory(writer)

    }

    final case class RetainerScope private(override val writer: PacketWriter, authorisedIds: String*)
            extends AbstractAttributesPresence with ChannelScope {

        override def sendToAll(packet: Packet, attributes: PacketAttributes): Unit = {
            defaultAttributes.drainAttributes(attributes)
            writer.writePacket(packet, attributes, authorisedIds: _*)
        }

        override def sendTo(packet: Packet, attributes: PacketAttributes, targetIDs: String*): Unit = {
            assertAuthorised(targetIDs: _*)
            defaultAttributes.drainAttributes(attributes)
            writer.writePacket(packet, attributes, targetIDs: _*)
        }

        override def sendToAll(packet: Packet): Unit = {
            sendToAll(packet, SimplePacketAttributes.empty)
        }

        override def sendTo(packet: Packet, targetIDs: String*): Unit = {
            sendTo(packet, SimplePacketAttributes.empty, targetIDs: _*)
        }

        override def areAuthorised(identifier: String*): Boolean = {
            authorisedIds.containsSlice(identifier)
        }

        override def canConflictWith(scope: ChannelScope): Boolean = {
            scope.areAuthorised(authorisedIds: _*)
        }

        override def equals(obj: Any): Boolean = {
            obj match {
                case s: RetainerScope => s.authorisedIds == this.authorisedIds
                case _                => false
            }
        }

        override def shareWriter[S <: ChannelScope](factory: ScopeFactory[S]): S = factory(writer)

    }

    def broadcast: ScopeFactory[BroadcastScope] = BroadcastScope(_)

    def discardCurrent: ScopeFactory[BroadcastScope] = writer => BroadcastScope(writer, writer.currentIdentifier)

    def discards(discarded: String*): ScopeFactory[BroadcastScope] = BroadcastScope(_, discarded: _*)

    def retains(authorised: String*): ScopeFactory[RetainerScope] = RetainerScope(_, authorised: _*)

}
