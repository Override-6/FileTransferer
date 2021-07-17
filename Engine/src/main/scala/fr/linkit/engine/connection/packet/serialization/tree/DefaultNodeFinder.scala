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

import fr.linkit.api.connection.packet.serialization.tree._
import fr.linkit.engine.connection.packet.serialization.tree.nodes._
import fr.linkit.engine.local.utils.{NumberSerializer, ScalaUtils}

import scala.reflect.ClassTag

class DefaultNodeFinder(context: DefaultSerialContext) extends NodeFinder {

    import context._

    override def getSerialNodeForType[T](clazz: Class[_], discard: Seq[Class[_ <: NodeFactory[_]]]): SerialNode[T] = {
        //println("getting...")
        //println(s"Getting serial not for $clazz :D")
        val node = getDefaultFactory(clazz, discard)
                .asInstanceOf[NodeFactory[T]]
        node.newNode(this, getClassProfile[T](clazz.asInstanceOf[Class[T]]))
    }

    override def getSerialNodeForRef[T: ClassTag](any: T, discard: Seq[Class[_ <: NodeFactory[_]]]): SerialNode[T] = {
        if (any == null)
            return NullNode
                    .newNode(this, getProfile[Null].asInstanceOf[ClassProfile[AnyRef]])
                    .asInstanceOf[SerialNode[T]]
        getSerialNodeForType[T](any.getClass.asInstanceOf[Class[_]], discard)
    }

    override def getProfile[T: ClassTag]: ClassProfile[T] = context.getProfile[T]

    override def getClassProfile[T](clazz: Class[_ <: T]): ClassProfile[T] = context.getClassProfile[T](clazz)

    override def listNodes[T](profile: ClassProfile[T], obj: T): List[SerialNode[_]] = {
        val fields = profile.desc.serializableFields
        fields.map(fields => {
            val fieldValue = fields.first.get(obj)
            getSerialNodeForRef(fieldValue)
        })
    }

    override def getDeserialNodeFor[T](bytes: Array[Byte]): DeserialNode[T] = {
        val seq = DefaultByteSeq(bytes)
        getDefaultFactory(seq, Seq.empty)
                .asInstanceOf[NodeFactory[T]]
                .newNode(this, seq)
    }

    private def getDefaultFactory[T](clazz: Class[T], discard: Seq[Class[_ <: NodeFactory[_]]]): NodeFactory[T] = {
        defaultFactories.find(s => !discard.contains(s.getClass) && s.canHandle(clazz))
                .getOrElse(throw new NoSuchElementException(s"Could not find factory for '$clazz'"))
                .asInstanceOf[NodeFactory[T]]
    }

    private def getDefaultFactory[T](seq: ByteSeq, discard: Seq[Class[_ <: NodeFactory[_]]]): NodeFactory[T] = {
        defaultFactories.find(s => !discard.contains(s.getClass) && s.canHandle(seq))
                .getOrElse(throw new NoSuchNodeFactoryException(s"Could not find factory for byte sequence '${ScalaUtils.toPresentableString(seq.array)}' (class of seq = ${seq.findClassOfSeq.orNull}), theorical seq class hashcode = ${NumberSerializer.deserializeInt(seq, 0)}"))
                .asInstanceOf[NodeFactory[T]]
    }
}
