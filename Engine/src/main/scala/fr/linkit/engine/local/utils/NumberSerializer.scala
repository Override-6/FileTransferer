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

package fr.linkit.engine.local.utils

object NumberSerializer {

    def serializeLong(value: Long): Array[Byte] = {
        Array[Byte](
            ((value >> 56) & 0xff).toByte,
            ((value >> 48) & 0xff).toByte,
            ((value >> 40) & 0xff).toByte,
            ((value >> 32) & 0xff).toByte,
            ((value >> 24) & 0xff).toByte,
            ((value >> 16) & 0xff).toByte,
            ((value >> 8) & 0xff).toByte,
            ((value >> 0) & 0xff).toByte
        )
    }

    def deserializeLong(bytes: Array[Byte], index: Int): Long = {
        //println("Deserializing long in zone " + new String(bytes.slice(index, index + 8)))
        //println("raw bytes = " + bytes.mkString("Array(", ", ", ")"))
        (0xff & bytes(index)) << 56 |
                (0xff & bytes(index + 1)) << 48 |
                (0xff & bytes(index + 2)) << 40 |
                (0xff & bytes(index + 3)) << 32 |
                (0xff & bytes(index + 4)) << 24 |
                (0xff & bytes(index + 5)) << 16 |
                (0xff & bytes(index + 6)) << 8 |
                (0xff & bytes(index + 7)) << 0
    }

    def deserializeInt(bytes: Array[Byte], index: Int): Int = {
        //println(s"Deserializing int from byte array ${ScalaUtils.toPresentableString(bytes.take(index + 4))}")
        val result = (0xff & bytes(index)) << 24 |
                ((0xff & bytes(index + 1)) << 16) |
                ((0xff & bytes(index + 2)) << 8) |
                ((0xff & bytes(index + 3)) << 0)
        //println(s"result = ${result}")
        result
    }

    def serializeInt(value: Int): Array[Byte] = {
        Array[Byte](
            ((value >> 24) & 0xff).toByte,
            ((value >> 16) & 0xff).toByte,
            ((value >> 8) & 0xff).toByte,
            ((value >> 0) & 0xff).toByte
        )
    }

    def serializeShort(value: Short): Array[Byte] = {
        Array[Byte](
            ((value >> 8) & 0xff).toByte,
            ((value >> 0) & 0xff).toByte
        )
    }

    def deserializeShort(bytes: Array[Byte], index: Int): Short = {
        (((0xff & bytes(index)) << 8) |
                ((0xff & bytes(index + 1)) << 0)).toShort
    }

    def serializeNumber(value: Long, insertFlag: Boolean = false): Array[Byte] = {
        //println(s"Serializing number $value, insertFlag: $insertFlag")

        def flagged(array: Array[Byte]): Array[Byte] = (if (insertFlag) Array(array.length.toByte) else Array()) ++ array

        if (Byte.MinValue <= value && value <= Byte.MaxValue) {
            //println("Byte")
            return flagged(Array(value.toByte))
        }

        if (Short.MinValue <= value && value <= Short.MaxValue) {
            //println(s"Short (${value.toShort}) - " + new String(serializeShort(value.toShort)))
            return flagged(serializeShort(value.toShort))
        }

        if (Int.MinValue <= value && value <= Int.MaxValue) {
            //println("Int")
            return flagged(serializeInt(value.toInt))
        }

        //println("Long")
        flagged(serializeLong(value))
    }

    /**
     * @return a pair with the deserialized number at left, and its length in the array at right.
     * */
    def deserializeFlaggedNumber[@specialized(Byte, Short, Int, Long) T <: AnyVal](bytes: Array[Byte], start: Int): (T, Byte) = {
        var result: Long = 0
        val numberLength = bytes(start)
        if (numberLength < 0)
            throw new IllegalArgumentException("number length < 0")
        if (numberLength == 1)
            return (bytes(start + 1).asInstanceOf[T], 2)

        val limit = start + numberLength

        for (i <- (start + 1) to limit) {
            val b     = bytes(i)
            val place = (limit - i) * 8
            result |= (0xff & b) << place
        }

        (result.asInstanceOf[T], (numberLength + 1).toByte)
    }

    def convertToShortArray(array: Array[Byte]): Array[Short] = {
        array.grouped(2).map(deserializeShort(_, 0)).toArray
    }

    def convertToIntArray(array: Array[Byte]): Array[Int] = {
        array.grouped(4).map(deserializeInt(_, 0)).toArray
    }

    def convertToLongArray(array: Array[Byte]): Array[Long] = {
        array.grouped(8).map(deserializeLong(_, 0)).toArray
    }

    def convertByteArray(array: Array[Short]): Array[Byte] = {
        array.flatMap(serializeShort)
    }

    def convertByteArray(array: Array[Int]): Array[Byte] = {
        array.flatMap(serializeInt)
    }

    def convertByteArray(array: Array[Long]): Array[Byte] = {
        array.flatMap(serializeLong)
    }

}
