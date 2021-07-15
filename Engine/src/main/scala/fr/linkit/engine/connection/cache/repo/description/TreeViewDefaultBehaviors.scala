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

import fr.linkit.api.connection.cache.repo.description.{MemberBehaviorFactory, TreeViewBehavior, WrapperBehavior}
import fr.linkit.engine.connection.cache.repo.description.annotation.AnnotationBasedMemberBehaviorFactory

import scala.collection.mutable
import scala.reflect.runtime.universe
import scala.reflect.{ClassTag, classTag}

class TreeViewDefaultBehaviors(memberBehaviorFactory: MemberBehaviorFactory) extends TreeViewBehavior {

    private val behaviors = mutable.HashMap.empty[Class[_], WrapperBehavior[_]]

    override def get[B: universe.TypeTag : ClassTag]: WrapperBehavior[B] = {
        getFromAnyClass(classTag[B].runtimeClass)
    }

    override def getFromClass[B](clazz: Class[B]): WrapperBehavior[B] = {
        getFromAnyClass[B](clazz)
    }

    private def getFromAnyClass[B](clazz: Class[_]): WrapperBehavior[B] = {
        behaviors.getOrElseUpdate(clazz, SimpleWrapperBehavior(SimplePuppetClassDescription(clazz), this, memberBehaviorFactory))
                .asInstanceOf[WrapperBehavior[B]]
    }

    override def put[B](clazz: Class[B], bhv: WrapperBehavior[B]): Unit = behaviors.put(clazz, bhv)

}