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

package fr.linkit.core.connection.network.cache.puppet

import fr.linkit.api.connection.packet.Packet
import fr.linkit.core.connection.packet.fundamental.RefPacket
import fr.linkit.core.connection.packet.fundamental.RefPacket.ObjectPacket
import fr.linkit.core.connection.packet.traffic.channel.request.ResponseSubmitter
import fr.linkit.core.local.utils.ScalaUtils

import java.lang.reflect.Modifier

case class ObjectChip[S <: Serializable] private(owner: String, puppet: S) {

    private val desc = PuppetClassFields.ofRef(puppet)

    def updateField(fieldName: String, value: Any): Unit = {
        desc.getSharedField(fieldName)
                .fold()(ScalaUtils.setFieldValue(_, puppet, value))
    }

    def updateAllFields(obj: Serializable): Unit = {
        desc.foreachSharedFields(field => {
            val value = field.get(obj)
            ScalaUtils.setFieldValue(field, puppet, value)
        })
    }

    def canCallMethod(methodName: String, parameterTypes: Seq[Class[_]]): Boolean = desc.getSharedMethod(methodName, parameterTypes).isDefined

    def callMethod(methodName: String, params: Seq[Serializable]): Serializable = {
        val parameterTypes = params.map(_.getClass)
        if (!canCallMethod(methodName, parameterTypes))
            throw new PuppetException(s"Attempted to invoke cached method '$methodName'")

        val method = desc.getSharedMethod(methodName, parameterTypes).get
        method.invoke(puppet, params: _*)
                .asInstanceOf[Serializable]
    }

    private[puppet] def handleBundle(packet: Packet, submitter: ResponseSubmitter): Unit = {
        packet match {
            case ObjectPacket((methodName: String, args: Array[Any])) =>
                var result: Serializable = null
                val castedArgs           = ScalaUtils.slowCopy[Serializable](args)
                if (canCallMethod(methodName, castedArgs.map(_.getClass))) {
                    result = callMethod(methodName, castedArgs)
                }
                submitter
                        .addPacket(RefPacket(result))
                        .submit()

            case ObjectPacket((fieldName: String, value: Any)) =>
                val field = desc.getSharedField(fieldName).get
                ScalaUtils.setFieldValue(field, puppet, value)
        }
    }

}

object ObjectChip {

    def apply[S <: Serializable](owner: String, puppet: S): ObjectChip[S] = {
        if (puppet == null)
            throw new NullPointerException("puppet is null !")
        val clazz = puppet.getClass

        if (Modifier.isFinal(clazz.getModifiers))
            throw new IllegalPuppetException("Puppet can't be final.")

        new ObjectChip[S](owner, puppet)
    }

}
