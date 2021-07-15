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

object NullNode extends NodeFactory[AnyRef] {

    val NullFlag: Array[Byte] = Array(-74)
    val NoneFlag: Array[Byte] = Array(-73)

    override def canHandle(clazz: Class[_]): Boolean = clazz == null || clazz == None.getClass

    override def canHandle(bytes: ByteSeq): Boolean = bytes.sameFlagAt(0, NullFlag(0))

    override def newNode(finder: NodeFinder, profile: ClassProfile[AnyRef]): SerialNode[AnyRef] = (n, b) => {
        NullFlag ++ (if (n eq None) NoneFlag else Array())
    }

    override def newNode(finder: NodeFinder, bytes: ByteSeq): DeserialNode[AnyRef] = () => {
        (if (bytes.length == 2) None else null).asInstanceOf[AnyRef]
    }
}
