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

package fr.linkit.engine.test.classes

import fr.linkit.api.connection.cache.repo.description.annotation.InvocationKind.ONLY_LOCAL

import scala.annotation.meta.getter
import fr.linkit.api.connection.cache.repo.description.annotation.{MethodControl => MC}

case class Player(@(MC@getter)(ONLY_LOCAL) id: Int,
                  @(MC@getter)(ONLY_LOCAL) owner: String,
                  @(MC@getter)(ONLY_LOCAL) var name: String,
                  @(MC@getter)(ONLY_LOCAL) var x: Long,
                  @(MC@getter)(ONLY_LOCAL) var y: Long) extends Serializable {

    def this(other: Player) = {
        this(other.id, other.owner, other.name, other.x, other.y)
    }

}
