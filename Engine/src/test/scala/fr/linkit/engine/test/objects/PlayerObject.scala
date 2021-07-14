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

package fr.linkit.engine.test.objects

import fr.linkit.api.connection.cache.repo.description.annotation.InvocationKind.ONLY_LOCAL
import fr.linkit.api.connection.cache.repo.description.annotation.MethodControl

import scala.annotation.meta._

case class PlayerObject(@(MethodControl @getter)(ONLY_LOCAL) id: Int,
                        @(MethodControl @getter)(ONLY_LOCAL) owner: String,
                        @(MethodControl @getter)(ONLY_LOCAL) var name: String,
                        @(MethodControl @getter)(ONLY_LOCAL) var x: Long,
                        @(MethodControl @getter)(ONLY_LOCAL) var y: Long) extends Serializable {

    def this(other: PlayerObject) = {
        this(other.id, other.owner, other.name, other.x, other.y)
    }

    @MethodControl(ONLY_LOCAL)
    override def toString: String = s"PlayerObject($id, $owner, $name, $x, $y)"
}
