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
import fr.linkit.api.connection.packet.serialization.tree.SerializableClassDescription.Fields

import java.lang.reflect.Field

trait SerializableClassDescription {
    val serializableFields: List[Fields]
    val signItemCount: Int
    val classCode    : Array[Byte]

    def foreachDeserializableFields(action: (Int, Field) => Unit): Unit

}

object SerializableClassDescription {
    case class Fields(first: Field, linked: Seq[Field])
}
