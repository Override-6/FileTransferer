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

package fr.linkit.engine.connection.packet.serialization.tree.nodes

import fr.linkit.api.connection.packet.serialization.StringRepresentable
import fr.linkit.api.connection.packet.serialization.tree._
import fr.linkit.engine.local.utils.NumberSerializer

import scala.reflect.{ClassTag, classTag}

object StringRepresentableNode {

    private val SRFlag: Array[Byte] = Array(-111)

    def apply[T: ClassTag](repr: StringRepresentable[T]): NodeFactory[T] = new NodeFactory[T] {
        private val clazz = classTag[T].runtimeClass

        override def canHandle(clazz: Class[_]): Boolean = {
            //println(s"clazz = ${clazz}")
            //println(s"this.clazz = ${this.clazz}")
            val result = this.clazz.isAssignableFrom(clazz)
            //println(s"result = ${result}")
            result
        }

        override def canHandle(bytes: ByteSeq): Boolean = {
            //println(s"bytes = ${ScalaUtils.toPresentableString(bytes.array)}")
            //println(s"raw bytes = ${bytes.array.mkString("Array(", ", ", ")")}")
            bytes.sameFlagAt(4, SRFlag(0)) && bytes.classExists(s => {
                //println(s"clazz = ${clazz}")
                //println(s"s = ${s}")
                clazz.isAssignableFrom(s)
            })
        }

        override def newNode(finder: NodeFinder, profile: ClassProfile[T]): SerialNode[T] = {
            new StringRepresentableSerialNode[T](profile, repr)
        }

        override def newNode(finder: NodeFinder, bytes: ByteSeq): DeserialNode[T] = {
            new StringRepresentableDeserialNode[T](finder.getProfile[T], bytes.array, repr)
        }
    }

    class StringRepresentableSerialNode[T](profile: ClassProfile[T], repr: StringRepresentable[T]) extends SerialNode[T] {

        override def serialize(t: T, putTypeHint: Boolean): Array[Byte] = {
            val typeBytes = NumberSerializer.serializeInt(t.getClass.getName.hashCode)
            typeBytes ++ SRFlag ++ repr.getRepresentation(t).getBytes
        }
    }

    class StringRepresentableDeserialNode[T](profile: ClassProfile[T], bytes: Array[Byte],
                                             repr: StringRepresentable[T]) extends DeserialNode[T] {

        override def deserialize(): T = {
            repr.fromRepresentation(new String(bytes.drop(5)))
        }
    }

}
