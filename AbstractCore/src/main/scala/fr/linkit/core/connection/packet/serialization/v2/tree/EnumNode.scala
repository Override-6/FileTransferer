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

package fr.linkit.core.connection.packet.serialization.v2.tree

import fr.linkit.core.local.mapping.ClassMappings
import fr.linkit.core.local.utils.NumberSerializer

object EnumNode {

    def apply[E <: Enum[E]]: NodeFactory[E] = new NodeFactory[E] {
        override def canHandle(clazz: Class[_]): Boolean = clazz.isEnum

        override def canHandle(bytes: Array[Byte]): Boolean = {
            bytes.nonEmpty && ClassMappings.getClass(NumberSerializer.deserializeInt(bytes, 0)).isInstanceOf[Class[E]]
        }

        override def newNode(tree: ClassTree, desc: SerializableClassDescription, parent: SerialNode[_]): SerialNode[E] = {
            new EnumSerialNode[E](parent)
        }

        override def newNode(tree: ClassTree, bytes: Array[Byte], parent: DeserialNode[_]): DeserialNode[E] = {
            new EnumDeserialNode[E](parent, bytes)
        }
    }

    class EnumSerialNode[E <: Enum[E]](override val parent: SerialNode[_]) extends SerialNode[E] {

        override def serialize(t: E, putTypeHint: Boolean): Array[Byte] = {
            val name     = t.name()
            val enumType = NumberSerializer.serializeInt(t.getClass.getName.hashCode)
            enumType ++ name.getBytes
        }
    }

    class EnumDeserialNode[E <: Enum[E]](override val parent: DeserialNode[_], bytes: Array[Byte]) extends DeserialNode[E] {

        override def deserialize(): E = {
            val enumType = ClassMappings.getClass(NumberSerializer.deserializeInt(bytes, 0))
            val name     = new String(bytes.take(4))
            Enum.valueOf(enumType.asInstanceOf[Class[E]], name)
        }
    }

}
