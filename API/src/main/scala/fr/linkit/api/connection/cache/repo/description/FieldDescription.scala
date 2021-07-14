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

package fr.linkit.api.connection.cache.repo.description

import fr.linkit.api.local.generation.PuppetClassDescription

import java.lang.reflect.Field
import scala.reflect.runtime.universe.MethodSymbol

case class FieldDescription(fieldGetter: MethodSymbol,
                            classDesc: PuppetClassDescription[_]) {

    val javaField: Field = classDesc.clazz.getDeclaredField(fieldGetter.name.toString)

    val fieldId: Int = {
        fieldGetter.hashCode() + fieldGetter.returnType.hashCode()
    }
}