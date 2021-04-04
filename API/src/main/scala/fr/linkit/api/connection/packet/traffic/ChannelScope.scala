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

package fr.linkit.api.connection.packet.traffic

import fr.linkit.api.connection.packet.Packet
import fr.linkit.api.connection.packet.traffic.ChannelScope.ScopeFactory
import fr.linkit.api.local.system.ForbiddenIdentifierException

trait ChannelScope {

    val writer: PacketWriter

    val traffic: PacketTraffic = writer.traffic

    def sendToAll(packet: Packet): Unit

    def sendTo(packet: Packet, targetIDs: String*): Unit

    def areAuthorised(identifiers: String*): Boolean

    def copyFromWriter(writer: PacketWriter): ChannelScope

    def canConflictWith(scope: ChannelScope): Boolean

    def assertAuthorised(identifiers: String*): Unit = {
        if (!areAuthorised(identifiers: _*))
            throw new ForbiddenIdentifierException(s"this identifier '${identifiers}' is not authorised by this scope.")
    }

    def shareWriter[S <: ChannelScope](factory: ScopeFactory[S]): S

    def equals(obj: Any): Boolean

}

object ChannelScope {

    final case class BroadcastScope private(override val writer: PacketWriter, transform: Packet => Packet) extends ChannelScope {

        override def sendToAll(packet: Packet): Unit = writer.writeBroadcastPacket(transform(packet))

        override def sendTo(packet: Packet, targetIDs: String*): Unit = {
            writer.writePacket(transform(packet), targetIDs: _*)
        }

        override def areAuthorised(identifiers: String*): Boolean = true //everyone is authorised in a BroadcastScope

        override def canConflictWith(scope: ChannelScope): Boolean = {
            //As Long As everyone is authorised by a BroadcastScope,
            //the other scope wouldn't conflict with this scope only if it discards all identifiers.
            scope.isInstanceOf[BroadcastScope] || scope.canConflictWith(this)
        }

        override def copyFromWriter(writer: PacketWriter): BroadcastScope = BroadcastScope(writer, transform)

        override def equals(obj: Any): Boolean = {
            obj.isInstanceOf[BroadcastScope]
        }

        override def shareWriter[S <: ChannelScope](factory: ScopeFactory[S]): S = factory(writer)
    }

    final case class ReservedScope private(override val writer: PacketWriter, transform: Packet => Packet, authorisedIds: String*) extends ChannelScope {

        override def sendToAll(packet: Packet): Unit = {
            authorisedIds.foreach(writer.writePacket(transform(packet), _))
        }

        override def sendTo(packet: Packet, targetIDs: String*): Unit = {
            assertAuthorised(targetIDs: _*)
            writer.writePacket(transform(packet), targetIDs: _*)
        }

        override def areAuthorised(identifier: String*): Boolean = {
            authorisedIds.containsSlice(identifier)
        }

        override def copyFromWriter(writer: PacketWriter): ReservedScope = ReservedScope(writer, transform, authorisedIds: _*)

        override def canConflictWith(scope: ChannelScope): Boolean = {
            scope.areAuthorised(authorisedIds: _*)
        }

        override def equals(obj: Any): Boolean = {
            obj match {
                case s: ReservedScope => s.authorisedIds == this.authorisedIds
                case _                => false
            }
        }

        override def shareWriter[S <: ChannelScope](factory: ScopeFactory[S]): S = factory(writer)

    }

    trait ScopeFactory[S <: ChannelScope] {

        def apply(writer: PacketWriter): S
    }

    def broadcast: ScopeFactory[BroadcastScope] = BroadcastScope(_, p => p)

    def broadcast(transform: Packet => Packet): ScopeFactory[BroadcastScope] = BroadcastScope(_, transform)

    def reserved(authorised: String*): ScopeFactory[ReservedScope] = ReservedScope(_, p => p, authorised: _*)

    def reserved(transform: Packet => Packet, authorised: String*): ScopeFactory[ReservedScope] = ReservedScope(_, transform, authorised: _*)

}