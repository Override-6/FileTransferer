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

package fr.`override`.linkit.api.packet.serialization

import fr.`override`.linkit.api.Relay
import fr.`override`.linkit.api.exception.PacketException
import fr.`override`.linkit.api.network.cache.SharedCacheHandler
import fr.`override`.linkit.api.network.cache.collection.SharedCollection
import fr.`override`.linkit.api.packet.PacketUtils.wrap
import fr.`override`.linkit.api.packet._
import fr.`override`.linkit.api.packet.serialization.ObjectSerializer

import scala.util.control.NonFatal

class PacketTranslator(relay: Relay) { //Notifier is accessible from api to reduce parameter number in (A)SyncPacketChannel

    def toPacketAndCoords(bytes: Array[Byte]): (Packet, PacketCoordinates) = {
        SmartSerializer.deserialize(bytes).swap
    }

    def fromPacketAndCoords(packet: Packet, coordinates: PacketCoordinates): Array[Byte] = {
        wrap(fromPacketAndCoordsNoWrap(packet, coordinates))
    }

    def fromPacketAndCoordsNoWrap(packet: Packet, coordinates: PacketCoordinates): Array[Byte] = {
        val bytes = SmartSerializer.serialize(packet, coordinates)
        relay.securityManager.hashBytes(bytes)
    }

    def completeInitialisation(cache: SharedCacheHandler): Unit = {
        SmartSerializer.completeInitialisation(cache)
    }

    private object SmartSerializer {
        private val rawSerializer = RawObjectSerializer
        @Nullable
        @volatile private var cachedSerializer: ObjectSerializer = _ //Will be instantiated once connection with the server is handled.
        @Nullable
        @volatile private var cachedSerializerWhitelist: SharedCollection[String] = _

        def serialize(packet: Packet, coordinates: PacketCoordinates): Array[Byte] = {
            //Thread.dumpStack()
            val serializer = if (initialised) {
                val whiteListArray = cachedSerializerWhitelist.toArray
                coordinates.determineSerializer(whiteListArray, rawSerializer, cachedSerializer)
            } else {
                rawSerializer
            }
            try {
                //println(s"Serializing $packet, $coordinates in thread ${Thread.currentThread()} with serializer ${serializer.getClass.getSimpleName}")
                serializer.serialize(Array(coordinates, packet))
            } catch {
                case NonFatal(e) =>
                    throw PacketException(s"Could not serialize packet and coordinates $packet, $coordinates.", e)
            }
        }

        def deserialize(bytes: Array[Byte]): (PacketCoordinates, Packet) = {
            val serializer = if (rawSerializer.isSameSignature(bytes)) {
                rawSerializer
            } else if (!initialised) {
                throw new IllegalStateException("Received cached serialisation signature but this packet translator is not ready to handle it.")
            } else {
                cachedSerializer
            }
            val array = try {
                serializer.deserializeAll(bytes)
            } catch {
                case NonFatal(e) =>
                    throw PacketException(s"Could not deserialize bytes ${new String(bytes)} to packet.", e)

            }
            //println(s"Deserialized ${array.mkString("Array(", ", ", ")")}")
            (array(0).asInstanceOf[PacketCoordinates], array(1).asInstanceOf[Packet])
        }

        def completeInitialisation(cache: SharedCacheHandler): Unit = {
            if (cachedSerializer != null)
                throw new IllegalStateException("This packet translator is already fully initialised !")

            cachedSerializer = new CachedObjectSerializer(cache)
            cachedSerializerWhitelist = cache.get(15, SharedCollection[String])
            cachedSerializerWhitelist.add(relay.identifier)
            println("COMPLETED PACKET TRANSLATOR INITIALISATION")
        }

        def initialised: Boolean = cachedSerializerWhitelist != null
    }

}
