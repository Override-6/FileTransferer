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

package fr.linkit.prototypes

import fr.linkit.api.connection.packet.DedicatedPacketCoordinates
import fr.linkit.api.local.ApplicationContext
import fr.linkit.core.connection.network.cache.puppet.generation.PuppetWrapperClassGenerator
import fr.linkit.core.connection.network.cache.puppet.{PuppetClassDesc, Puppeteer}
import fr.linkit.core.connection.packet.SimplePacketAttributes
import fr.linkit.core.connection.packet.fundamental.RefPacket.ObjectPacket
import fr.linkit.core.connection.packet.serialization.DefaultSerializer
import fr.linkit.core.connection.packet.traffic.channel.request.RequestPacket
import fr.linkit.core.local.mapping.{ClassMapEngine, ClassMappings}
import fr.linkit.core.local.system.fsa.LocalFileSystemAdapters
import fr.linkit.core.local.system.fsa.nio.{NIOFileAdapter, NIOFileSystemAdapter}
import fr.linkit.core.local.utils.ScalaUtils

import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant

object Tests {

    private val fsa = LocalFileSystemAdapters.Nio

    doMappings()

    //private val generatedPuppet = getTestPuppet

    private val coords     = DedicatedPacketCoordinates(12, "TestServer1", "s1")
    private val attributes = SimplePacketAttributes.from("cache" -> 27, "id" -> -192009448, "family" -> "s1")
    private val packet     = RequestPacket(9, Array(ObjectPacket(Array(Path.of("C:\\Users\\maxim\\Desktop\\fruits")))))

    def main(args: Array[String]): Unit = {

        ClassMappings.getClassOpt(classOf[Timestamp].getName.hashCode()).get

        PuppetWrapperClassGenerator.getOrGenerate(classOf[NIOFileSystemAdapter])
        PuppetWrapperClassGenerator.getOrGenerate(classOf[NIOFileAdapter])

        val serializer = new DefaultSerializer
        val ref   = Array(coords, attributes, packet)
        val bytes = serializer.serialize(ref, true)
        println(s"bytes = ${ScalaUtils.toPresentableString(bytes)} (l: ${bytes.length})")
        val result = serializer.deserializeAll(bytes)
        println(s"result = ${result.mkString("Array(", ", ", ")")}")
    }

    private def doMappings(): Unit = {
        ClassMapEngine.mapAllSourcesOfClasses(fsa, Seq(getClass, ClassMapEngine.getClass, Predef.getClass, classOf[ApplicationContext]))
        ClassMapEngine.mapJDK(fsa)
    }

    private def getTestPuppet: Unit = {
        /*val clazz = PuppetWrapperClassGenerator.getOrGenerate(classOf[NIOFileSystemAdapter])
        clazz.getConstructor(classOf[Puppeteer[_]], classOf[NIOFileSystemAdapter]).newInstance(new Puppeteer[NIOFileSystemAdapter](
            null,
            null,
            -4,
            "stp",
            PuppetClassDesc.ofClass(classOf[NIOFileSystemAdapter]
            )), LocalFileSystemAdapters.Nio)*/
    }
}
