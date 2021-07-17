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

package fr.linkit.engine.connection.cache.repo.description.annotation

import fr.linkit.api.connection.cache.repo.description._
import fr.linkit.api.connection.cache.repo.description.annotation._
import fr.linkit.engine.connection.cache.repo.description.annotation.AnnotationBasedMemberBehaviorFactory.DefaultMethodControl
import fr.linkit.engine.connection.cache.repo.invokation.local.SimpleRMIHandler

import scala.reflect.runtime.universe

class AnnotationBasedMemberBehaviorFactory(handler: RMIHandler = SimpleRMIHandler) extends MemberBehaviorFactory {

    def getSynchronizedParams(symbol: universe.MethodSymbol): Seq[Boolean] = {
        val params = symbol
                .paramLists
                .flatten
                .map(_.annotations
                        .exists(_.tree
                                .tpe
                                .typeSymbol
                                .fullName == classOf[SynchronizeParam].getName)
                )
        params
    }

    override def genMethodBehavior(desc: MethodDescription): MethodBehavior = {
        val javaMethod         = desc.javaMethod
        val control            = Option(javaMethod.getAnnotation(classOf[MethodControl])).getOrElse(DefaultMethodControl)
        val synchronizedParams = getSynchronizedParams(desc.symbol)
        val invocationKind     = control.value()
        val invokeOnly         = control.invokeOnly()
        val isHidden           = control.hide()
        val syncReturnValue    = control.synchronizeReturnValue()
        MethodBehavior(
            desc, invokeOnly, synchronizedParams,
            invocationKind, syncReturnValue, isHidden, handler
        )
    }

    override def genFieldBehavior(desc: FieldDescription): FieldBehavior = {
        val control        = Option(desc.javaField.getAnnotation(classOf[FieldControl]))
        val isSynchronized = control.exists(_.synchronize())
        FieldBehavior(desc, isSynchronized)
    }

}

object AnnotationBasedMemberBehaviorFactory {

    def apply(handler: RMIHandler = SimpleRMIHandler): AnnotationBasedMemberBehaviorFactory = new AnnotationBasedMemberBehaviorFactory(handler)

    private val DefaultMethodControl: MethodControl = {
        new MethodControl {
            override def value(): InvocationKind = InvocationKind.LOCAL_AND_REMOTES

            override def synchronizeReturnValue(): Boolean = false

            override def hide(): Boolean = false

            override def invokeOnly(): Boolean = false

            override def annotationType(): Class[_ <: java.lang.annotation.Annotation] = getClass
        }
    }
}
