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

import fr.linkit.api.connection.packet.serialization.tree._
import fr.linkit.engine.connection.packet.serialization.tree._
import fr.linkit.engine.local.utils.{NumberSerializer, ScalaUtils}
import fr.linkit.engine.local.utils.ScalaUtils.findUnsafe

object ObjectNode {

    val Constraints   : Array[Class[_] => Boolean] = Array(_.isPrimitive, _.isArray, _.isEnum, _ == classOf[String])
    val NullObjectFlag: Byte                       = -76

    def apply: NodeFactory[Any] = new NodeFactory[Any] {
        override def canHandle(clazz: Class[_]): Boolean = {
            !Constraints.exists(_ (clazz))
        }

        override def canHandle(bytes: ByteSeq): Boolean = {
            bytes.sameFlagAt(0, NullObjectFlag) || bytes.isClassDefined
        }

        override def newNode(finder: NodeFinder, profile: ClassProfile[Any]): SerialNode[Any] = {
            new ObjectSerialNode(profile, finder)
        }

        override def newNode(finder: NodeFinder, bytes: ByteSeq): DeserialNode[Any] = {
            new ObjectDeserialNode(finder.getClassProfile(bytes.getClassOfSeq), bytes, finder)
        }
    }

    private val TheUnsafe = findUnsafe()

    class ObjectSerialNode(profile: ClassProfile[Any], context: NodeFinder) extends SerialNode[Any] {

        override def serialize(t: Any, putTypeHint: Boolean): Array[Byte] = {
           //println(s"Serializing Object ${t}")

            val desc = profile.desc
           //println(s"Object desc = ${desc}")
            profile.applyAllSerialProcedures(t)

            if (t == null)
                return Array(NullObjectFlag)

           //println(s"t.getClass = ${t.getClass} (${t.getClass.getName.hashCode()})")
            val children = context.listNodes[Any](profile, t)
           //println(s"children = ${children}")

            val classType = desc.classCode
           //println(s"NumberSerializer.deserializeInt(classType) = ${NumberSerializer.deserializeInt(classType, 0)}")
            val sign      = LengthSign.of(t, desc, children).toBytes
            //println(s"sign = ${toPresentableString(sign)}")
            val bytes     = classType ++ sign
            //println(s"Result of Object ${t} = ${ScalaUtils.toPresentableString(bytes)}")
            bytes
        }
    }

    class ObjectDeserialNode(profile: ClassProfile[Any], bytes: ByteSeq, context: NodeFinder) extends DeserialNode[Any] {

        override def deserialize(): Any = {
            if (bytes(0) == NullObjectFlag)
                return null

            //println(s"Deserializing object from bytes ${ScalaUtils.toPresentableString(bytes)}")

            //println(s"objectType = ${bytes.getClassOfSeq}")
            val desc = profile.desc
            //println(s"Object desc = ${desc}")

            val sign     = LengthSign.from(desc.signItemCount, bytes, bytes.length, 4)
            val instance = TheUnsafe.allocateInstance(bytes.getClassOfSeq)

            val fieldValues = for (childBytes <- sign.childrenBytes) yield {
                //println(s"childBytes (str) = ${ScalaUtils.toPresentableString(childBytes)}")
                //println(s"childBytes = ${childBytes.mkString("Array(", ", ", ")")}")
                val node   = context.getDeserialNodeFor[Any](childBytes)
                //println(s"node = ${node}")
                val result = node.deserialize()
                //println(s"result = $result")
                result
            }

            desc.foreachDeserializableFields { (i, field) =>
                //println(s"For object field number (of object $objectType) $i: ")
                //println(s"Deserialized value : ${fieldValues(i)}")
                ScalaUtils.setValue(instance, field, fieldValues(i))
            }

            //println(s"(objectType) = ${objectType}")
            //Try(//println(s"Instance = $instance"))
            profile.applyAllDeserialProcedures(instance)
            instance.asInstanceOf[Serializable]
        }

    }

}
