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

package fr.linkit.core.connection.packet.serialization.tree

import fr.linkit.core.connection.packet.serialization.Procedure

import scala.reflect.ClassTag

trait ContextHolder {

    def attachFactory(nodeFactory: NodeFactory[_]): Unit

    def detachFactory(nodeFactory: NodeFactory[_]): Unit

    def attachProcedure[C: ClassTag](procedure: Procedure[C]): Unit

    def detachProcedure[C: ClassTag](procedure: Procedure[C]): Unit

}
