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

package fr.linkit.engine.connection.cache.repo.description

import fr.linkit.api.connection.cache.repo.description._
import fr.linkit.api.local.generation.PuppetClassDescription

import scala.reflect.runtime.universe._

class SimpleWrapperBehavior[A] private(override val classDesc: PuppetClassDescription[A],
                                       override val treeView: TreeViewBehavior,
                                       factory: MemberBehaviorFactory) extends WrapperBehavior[A] {

    private val methods = {
        classDesc.listMethods()
                .map(factory.genMethodBehavior)
                .map(bhv => bhv.desc.methodId -> bhv)
                .toMap
    }

    private val fields = {
        classDesc.listFields()
                .map(factory.genFieldBehavior)
                .map(bhv => bhv.desc.fieldId -> bhv)
                .toMap
    }

    override def listMethods(): Iterable[MethodBehavior] = {
        methods.values
    }

    override def getMethodBehavior(id: Int): Option[MethodBehavior] = methods.get(id)

    override def listField(): Iterable[FieldBehavior] = {
        fields.values
    }

    override def getFieldBehavior(id: Int): Option[FieldBehavior] = fields.get(id)

}

object SimpleWrapperBehavior {

    def apply[A](classDesc: PuppetClassDescription[A], treeView: TreeViewBehavior, factory: MemberBehaviorFactory): SimpleWrapperBehavior[A] = {
        val bhv = new SimpleWrapperBehavior(classDesc, treeView, factory)
        treeView.put(classDesc.clazz, bhv)
        bhv
    }

    def toSynchronisedParamsIndexes(literal: String, method: Symbol): Seq[Boolean] = {
        val synchronizedParamNumbers = literal
                .split(",")
                .filterNot(s => s == "this" || s.isBlank)
                .map(s => s.trim
                        .dropRight(s.lastIndexWhere(!_.isDigit))
                        .toInt)
                .distinct
        for (n <- 1 to method.asMethod.paramLists.flatten.size) yield {
            synchronizedParamNumbers.contains(n)
        }
    }

}
