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
import fr.linkit.engine.local.mapping.ClassMappings
import fr.linkit.engine.local.utils.NumberSerializer

import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, SeqFactory, SeqOps, mutable}

object SeqNode {

    def ofMutable: NodeFactory[mutable.Seq[_]] = new NodeFactory[mutable.Seq[_]] {
        override def canHandle(cl: Class[_]): Boolean = {
            (cl ne classOf[::[_]]) && (cl ne Nil.getClass) && classOf[mutable.Seq[_]].isAssignableFrom(cl) && findFactory(cl).isDefined
        }

        override def canHandle(bytes: ByteSeq): Boolean = {
            bytes.classExists(canHandle)
        }

        override def newNode(finder: NodeFinder, profile: ClassProfile[mutable.Seq[_]]): SerialNode[mutable.Seq[_]] = {
            new MutableSeqSerialNode(profile, finder)
        }

        override def newNode(finder: NodeFinder, bytes: ByteSeq): DeserialNode[mutable.Seq[_]] = {
            new MutableSeqDeserialNode(finder.getClassProfile(bytes.getClassOfSeq), bytes, finder)
        }
    }

    def ofImmutable: NodeFactory[Seq[_]] = new NodeFactory[Seq[_]] {
        override def canHandle(clazz: Class[_]): Boolean = {
            clazz != classOf[::[_]] && clazz != Nil.getClass && classOf[Seq[_]].isAssignableFrom(clazz) && findFactory(clazz).isDefined
        }

        override def canHandle(bytes: ByteSeq): Boolean = {
            bytes.classExists(canHandle)
        }

        override def newNode(finder: NodeFinder, profile: ClassProfile[Seq[_]]): SerialNode[Seq[_]] = {
            new ImmutableSeqSerialNode(profile, finder)
        }

        override def newNode(context: NodeFinder, bytes: ByteSeq): DeserialNode[Seq[_]] = {
            new ImmutableSeqDeserialNode(context.getClassProfile(bytes.getClassOfSeq), bytes, context)
        }
    }

    class MutableSeqSerialNode(profile: ClassProfile[mutable.Seq[_]], finder: NodeFinder) extends SerialNode[mutable.Seq[_]] {

        override def serialize(t: mutable.Seq[_], putTypeHint: Boolean): Array[Byte] = {
            profile.applyAllSerialProcedures(t)

            val content      = t.toArray
            val seqTypeBytes = NumberSerializer.serializeInt(t.getClass.getName.hashCode)
            //println(s"content = ${content.mkString("Array(", ", ", ")")}")
            seqTypeBytes ++ finder.getSerialNodeForRef(content).serialize(awfulCast(content), putTypeHint)
        }
    }

    class MutableSeqDeserialNode(profile: ClassProfile[mutable.Seq[_]], bytes: Array[Byte], finder: NodeFinder) extends DeserialNode[mutable.Seq[_]] {

        override def deserialize(): mutable.Seq[_] = {
            val seqType = ClassMappings.getClass(NumberSerializer.deserializeInt(bytes, 0))
            //println(s"List type = ${seqType}")
            val factory = findFactory(seqType)
            //println(s"factory = ${factory}")
            //println(s"bytes.drop(4) = ${new String(bytes.drop(4))}")
            val content = finder.getDeserialNodeFor[Array[Any]](bytes.drop(4)).deserialize()
            //println(s"SeqNode: content = ${content.mkString("Array(", ", ", ")")}")
            if (content.isEmpty)
                return awfulCast(seqType.getConstructor().newInstance())

            val result = awfulCast[mutable.Seq[_]](factory.get.from(ArrayBuffer.from(content)))
            profile.applyAllDeserialProcedures(result)
            result
        }
    }

    class ImmutableSeqSerialNode(profile: ClassProfile[Seq[_]], finder: NodeFinder) extends SerialNode[Seq[_]] {

        override def serialize(t: Seq[_], putTypeHint: Boolean): Array[Byte] = {
            profile.applyAllSerialProcedures(t)
            val content      = t.toArray
            val seqTypeBytes = NumberSerializer.serializeInt(t.getClass.getName.hashCode)
            seqTypeBytes ++ finder.getSerialNodeForRef(content).serialize(awfulCast(content), putTypeHint)
        }
    }

    class ImmutableSeqDeserialNode(profile: ClassProfile[Seq[_]], bytes: Array[Byte], finder: NodeFinder) extends DeserialNode[Seq[_]] {

        override def deserialize(): Seq[_] = {
            val seqType = ClassMappings.getClass(NumberSerializer.deserializeInt(bytes, 0))
            val factory = findFactory(seqType)
            val content = finder.getDeserialNodeFor(bytes.drop(4)).deserialize()
            val result = awfulCast(factory.get.from(content))
            profile.applyAllDeserialProcedures(result)
            result
        }
    }

    private def findFactory[CC[A] <: SeqOps[A, Seq, Seq[A]]](seqType: Class[_]): Option[SeqFactory[CC]] = {
        try {
            val companionClass = Class.forName(seqType.getName + "$")
            val companion      = companionClass.getField("MODULE$").get(null)
            //println(s"companion = ${companion}")
            companion match {
                case e: SeqFactory[CC] => Option(e)
                case _                 => None
            }
        } catch {
            case _: ClassNotFoundException => None
        }
    }

    private def awfulCast[T](any: Any): T = any.asInstanceOf[T]

}
