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

package fr.linkit.engine.connection.packet.serialization.tree

import fr.linkit.api.connection.packet.serialization.tree.{SerialNode, SerializableClassDescription}
import fr.linkit.engine.local.utils.{NumberSerializer, ScalaUtils}

case class LengthSign(lengths: Array[Int], childrenBytes: Array[Array[Byte]]) {

    if (lengths.exists(_ < 0))
        throw new IllegalArgumentException("LengthSign contains negative length")

    def toBytes: Array[Byte] = {
       //println("toBytes invoked:")
       //println(s"  lengths = ${lengths.mkString("Array(", ", ", ")")}")
        val lengthsBytes = lengths.flatMap(l => NumberSerializer.serializeNumber(l, true))
       //println(s"  lengthsBytes = ${ScalaUtils.toPresentableString(lengthsBytes)}")
       //println(s"  children bytes = ${ScalaUtils.toPresentableString(childrenBytes.flatten)}")
        val result = lengthsBytes ++ childrenBytes.flatten
       //println("   Result = " + ScalaUtils.toPresentableString(result))
       //println("End.")
        result
    }

}

object LengthSign {

    def of(root: Any, desc: SerializableClassDescription, children: Iterable[SerialNode[_]]): LengthSign = {
        //println()
       //println(s"--- Creating sign for object $root")
        if (children.isEmpty && desc.signItemCount == -1)
            return LengthSign(Array(), Array())

        val lengths    = new Array[Int](children.size - 1) //Discard the last field, we already know its length by deducting it from previous lengths
        val byteArrays = new Array[Array[Byte]](children.size)

        val fieldValues = desc.serializableFields
                .map(_.first.get(root))

       //println(s"fieldValues = ${fieldValues}")

        var i = 0
       //println("-- Setting children")
        for (child <- children) {
           //println(s"--  For child $child ($i): ")
            val fieldValue = fieldValues(i)
            //Try(//println(s"    fieldValue = ${fieldValue} of type ${fieldValue.getClass}"))
            //Try(//println(s"fieldValueBytes = ${new String(NumberSerializer.serializeInt(fieldValue.getClass.getName.hashCode))}"))
            val bytes      = child.serialize(cast(fieldValue), true)
           //println(s"    child = ${fieldValue}")
           //println(s"    child bytes = ${ScalaUtils.toPresentableString(bytes)}")
           //println(s"    raw child bytes = ${bytes.mkString("Array(", ", ", ")")}")

            byteArrays(i) = bytes
            if (i < lengths.length)
                lengths(i) = bytes.length
           //println(s"    lengths = ${lengths.mkString("Array(", ", ", ")")}")
            i += 1
        }
       //println("Done.")
       //println()
        LengthSign(lengths, byteArrays)
    }

    def from(signItemCount: Int, bytes: Array[Byte], totalObjectLength: Int, start: Int): LengthSign = {
       //println()
       //println(s"--- Reading sign from bytes ${ScalaUtils.toPresentableString(bytes.drop(start))}")
       //println(s"signItemCount = ${signItemCount}")
       //println(s"totalObjectLength = ${totalObjectLength}")
       //println(s"start = ${start}")

        if (signItemCount == -1)
            return LengthSign(Array(), Array())

        val lengths            = new Array[Int](signItemCount) //Discard the last field, we already know its length by deducting it from totalObjectLength
        val childrenByteArrays = new Array[Array[Byte]](signItemCount + 1)

        var currentIndex = start
       //println("-- Reading lengths ")
        for (i <- 0 until signItemCount) {
           //println(s"FOR LENGTH ${i}: ")
            val (length, lengthByteCount: Byte) = NumberSerializer.deserializeFlaggedNumber[Int](bytes, currentIndex)
            lengths(i) = length
           //println(s"length = ${length}")
           //println(s"lengthByteCount = ${lengthByteCount}")
           //println(s"lengths = ${lengths.mkString("Array(", ", ", ")")}")
            currentIndex += lengthByteCount
           //println(s"currentIndex is now = ${currentIndex}")
        }

        //println("-- Reading children bytes")
        for (i <- 0 to signItemCount) {
            //println(s"- FOR CHILD ${i}: ")
            val childrenSize  = if (i == lengths.length) totalObjectLength - currentIndex else lengths(i)
            //println(s"childrenSize = ${childrenSize}")
            //println(s"currentIndex = ${currentIndex}")
            val childrenBytes = bytes.slice(currentIndex, currentIndex + childrenSize)
            //println(s"childrenBytes = ${childrenBytes.mkString("Array(", ", ", ")")}")
            //println(s"bytes = ${bytes.mkString("Array(", ", ", ")")}")
            //println(s"childrenBytes = ${ScalaUtils.toPresentableString(childrenBytes)}")
            childrenByteArrays(i) = childrenBytes
            currentIndex += childrenSize
        }
        //println()
        LengthSign(lengths, childrenByteArrays)
    }

    private def cast[T](any: Any): T = {
        any.asInstanceOf[T]
    }

}
