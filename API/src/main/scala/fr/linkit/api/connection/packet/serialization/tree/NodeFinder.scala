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

package fr.linkit.api.connection.packet.serialization.tree
import scala.reflect.ClassTag

trait NodeFinder extends ClassProfileHandler {

    def getSerialNodeForRef[T: ClassTag](any: T, discard: Seq[Class[_ <: NodeFactory[_]]] = Seq.empty): SerialNode[T]

    def getSerialNodeForType[T](clazz: Class[_], discard: Seq[Class[_ <: NodeFactory[_]]] = Seq.empty): SerialNode[T]

    def getDeserialNodeFor[T](bytes: Array[Byte]): DeserialNode[T]

    def listNodes[T](profile: ClassProfile[T], obj: T): List[SerialNode[_]]

    def listNodes[T](clazz: Class[T], obj: T): List[SerialNode[_]] = {
        listNodes(getClassProfile[T](clazz), obj)
    }
}
