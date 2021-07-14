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

package fr.linkit.engine.connection.cache.repo.invokation.local

import fr.linkit.api.connection.cache.repo._
import fr.linkit.api.connection.cache.repo.description.WrapperBehavior
import fr.linkit.api.connection.packet.Packet
import fr.linkit.engine.connection.packet.fundamental.RefPacket
import fr.linkit.engine.connection.packet.fundamental.RefPacket.ObjectPacket
import fr.linkit.engine.connection.packet.traffic.channel.request.ResponseSubmitter
import fr.linkit.engine.local.utils.ScalaUtils

import java.lang.reflect.Modifier
import scala.util.control.NonFatal

case class ObjectChip[S] private(owner: String,
                                 behavior: WrapperBehavior[S],
                                 wrapper: S with PuppetWrapper[S]) extends Chip[S] {

    override def updateObject(obj: S): Unit = {
        ScalaUtils.pasteAllFields(wrapper, obj)
    }

    override def callMethod(methodID: Int, params: Array[Array[Any]]): Any = {
        val methodDesc = behavior.getMethodBehavior(methodID)
        if (methodDesc.forall(_.isHidden)) {
            throw new PuppetException(s"Attempted to invoke ${methodDesc.fold("unknown")(_ => "hidden")} method '${
                methodDesc.map(_.desc.symbol.name.toString).getOrElse(s"(unknown method id '$methodID')")
            }(${params.mkString(", ")}) in class ${methodDesc.get.desc.symbol}'")
        }
        wrapper.getChoreographer.forceLocalInvocation {
            methodDesc.get
                    .desc
                    .javaMethod
                    .invoke(wrapper, params.flatten: _*)
        }
    }

    private[repo] def handleBundle(packet: Packet, submitter: ResponseSubmitter): Unit = {
        packet match {
            case ObjectPacket((methodId: Int, args: Array[Array[Any]])) =>
                var result: Any = null
                try {
                    result = callMethod(methodId, args)
                } catch {
                    case NonFatal(e) =>
                        //FIXME instance loop for serializers.
                        throw e
                    //result = ThrowableWrapper(e)
                }
                submitter
                        .addPacket(RefPacket(result))
                        .submit()
        }
    }

}

object ObjectChip {

    def apply[S](owner: String, behavior: WrapperBehavior[S], puppet: S with PuppetWrapper[S]): ObjectChip[S] = {
        if (puppet == null)
            throw new NullPointerException("puppet is null !")
        val clazz = puppet.getClass

        if (Modifier.isFinal(clazz.getModifiers))
            throw new IllegalPuppetException("Puppet can't be final.")

        new ObjectChip[S](owner, behavior, puppet)
    }

}
