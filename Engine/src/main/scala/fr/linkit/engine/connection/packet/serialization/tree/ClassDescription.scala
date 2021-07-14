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

import fr.linkit.api.connection.packet.serialization.tree.SerializableClassDescription
import fr.linkit.api.connection.packet.serialization.tree.SerializableClassDescription.Fields
import fr.linkit.engine.local.utils.NumberSerializer

import java.lang.reflect.{Field, Modifier}

class ClassDescription(val clazz: Class[_]) extends SerializableClassDescription {

    val serializableFields: List[Fields] = listSerializableFields(clazz)
    val signItemCount     : Int          = serializableFields.length - 1
    val classCode         : Array[Byte]  = NumberSerializer.serializeInt(clazz.getName.hashCode)

    override def foreachDeserializableFields(action: (Int, Field) => Unit): Unit = {
        var i = 0
        serializableFields.foreach(fields => {
            action(i, fields.first)
            fields.linked.foreach(action(i, _))
            i += 1
        })
    }

    override def toString: String = s"SerializableClassDescription($clazz, $serializableFields)"

    private def listSerializableFields(cl: Class[_]): List[Fields] = {
        if (cl == null)
            return List()

        def listAllSerialFields(cl: Class[_]): Seq[Field] = {
            if (cl == null)
                return Seq.empty
            cl.getDeclaredFields
                    .filterNot(p => Modifier.isTransient(p.getModifiers) || Modifier.isStatic(p.getModifiers))
                    .tapEach(_.setAccessible(true))
                    .toList ++ listAllSerialFields(cl.getSuperclass)
        }

        listAllSerialFields(cl)
                .groupBy(f => f.getName -> f.getType)
                .map(fields => Fields(fields._2.head, fields._2.drop(1)))
                .toList
        //}
    }

}

