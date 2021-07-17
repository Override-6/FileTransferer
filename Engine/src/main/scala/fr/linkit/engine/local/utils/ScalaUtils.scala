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

import fr.linkit.api.connection.packet.Packet
import fr.linkit.engine.connection.packet.UnexpectedPacketException
import sun.misc.Unsafe

import java.io.File
import java.lang.reflect.{Field, Modifier}
import scala.reflect.{ClassTag, classTag}
import scala.util.control.NonFatal

object ScalaUtils {

    def slowCopy[A: ClassTag](origin: Array[_ <: Any]): Array[A] = {
        val buff = new Array[A](origin.length)
        var i    = 0
        try {
            origin.foreach(anyRef => {
                buff(i) = anyRef.asInstanceOf[A]
                i += 1
            })
            buff
        } catch {
            case NonFatal(e) =>
                //println("Was Casting to " + classTag[A].runtimeClass)
                //println(s"Origin = ${origin.mkString("Array(", ", ", ")")}")
                //println(s"Failed when casting ref : ${origin(i)} at index $i")
                throw e
        }
    }

    def ensurePacketType[P <: Packet : ClassTag](packet: Packet): P = {
        val rClass = classTag[P].runtimeClass
        packet match {
            case p: P => p
            case null => throw new NullPointerException("Received null packet.")
            case p    => throw UnexpectedPacketException(s"Received unexpected packet type (${p.getClass.getName}), requested : ${rClass.getName}")
        }
    }

    implicit def deepToString(array: Array[Any]): String = {
        val sb = new StringBuilder("Array(")
        array.foreach {
            case subArray: Array[Any] => sb.append(deepToString(subArray))
            case any: Any             => sb.append(any).append(", ")
        }
        sb.dropRight(2) //remove last ", " string.
                .append(')')
                .toString()
    }

    implicit def deepToString(any: Any): String = {
        any match {
            case array: Array[Any] => deepToString(array)
            case any               => any.toString
        }
    }

    def setValue(instance: Any, field: Field, value: Any): Unit = {
        val fieldOffset = TheUnsafe.objectFieldOffset(field)
        import UnWrapper.unwrap

        import java.lang

        val action: (Any, Long) => Unit = if (field.getType.isPrimitive) {
            value match {
                case i: Integer      => TheUnsafe.putInt(_, _, i)
                case b: lang.Byte    => TheUnsafe.putByte(_, _, b)
                case s: lang.Short   => TheUnsafe.putShort(_, _, s)
                case l: lang.Long    => TheUnsafe.putLong(_, _, l)
                case d: lang.Double  => TheUnsafe.putDouble(_, _, d)
                case f: lang.Float   => TheUnsafe.putFloat(_, _, f)
                case b: lang.Boolean => TheUnsafe.putBoolean(_, _, b)
                case c: Character    => TheUnsafe.putChar(_, _, c)
            }
        } else field.getType match {
            case c if c eq classOf[Integer]      => TheUnsafe.putObject(_, _, unwrap(value, _.intValue))
            case c if c eq classOf[lang.Byte]    => TheUnsafe.putObject(_, _, unwrap(value, _.byteValue))
            case c if c eq classOf[lang.Short]   => TheUnsafe.putObject(_, _, unwrap(value, _.shortValue))
            case c if c eq classOf[lang.Long]    => TheUnsafe.putObject(_, _, unwrap(value, _.longValue))
            case c if c eq classOf[lang.Double]  => TheUnsafe.putObject(_, _, unwrap(value, _.doubleValue))
            case c if c eq classOf[lang.Float]   => TheUnsafe.putObject(_, _, unwrap(value, _.floatValue))
            case c if c eq classOf[lang.Boolean] => TheUnsafe.putObject(_, _, unwrap(value, _.booleanValue))
            case c if c eq classOf[Character]    => TheUnsafe.putObject(_, _, unwrap(value, _.charValue))
            case _                               => TheUnsafe.putObject(_, _, value)
        }
        action(instance, fieldOffset)
    }

    implicit def toPresentableString(bytes: Array[Byte]): String = {
        new String(bytes)
                .replace("\r", "R")
                .replace("\n", "N")
    }

    def formatPath(path: String): String = path
            .replace("\\", File.separator)
            .replace("//", File.separator)

    private val TheUnsafe = findUnsafe()

    @throws[IllegalAccessException]
    def findUnsafe(): Unsafe = {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        for (field <- unsafeClass.getDeclaredFields) {
            if (field.getType eq unsafeClass) {
                field.setAccessible(true)
                return field.get(null).asInstanceOf[Unsafe]
            }
        }
        throw new IllegalStateException("No instance of Unsafe found")
    }



    def pasteAllFields[A](instance: A, data: A): Unit = {
        retrieveAllFields(data.getClass)
                .foreach(field => {
                    field.setAccessible(true)
                    ScalaUtils.setValue(instance, field, field.get(data))
                })
    }

    def allocate[A](clazz: Class[_]): A = {
        if (clazz == null)
            throw new NullPointerException
        TheUnsafe.allocateInstance(clazz).asInstanceOf[A]
    }

    def retrieveAllFields(clazz: Class[_]): Seq[Field] = {
        var superClass = clazz
        var superFields = Seq.empty[Field]
        while (superClass != null) {
            superFields ++= superClass.getDeclaredFields
            superClass = superClass.getSuperclass
        }
        superFields
                .filterNot(f => Modifier.isStatic(f.getModifiers) || Modifier.isNative(f.getModifiers))
    }

}
