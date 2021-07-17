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

package fr.linkit.api.connection.cache.repo

import fr.linkit.api.connection.cache.repo.description.{PuppeteerInfo, TreeViewBehavior, WrapperBehavior}
import fr.linkit.api.connection.cache.repo.tree.PuppetCenter
import fr.linkit.api.connection.packet.PacketAttributesPresence

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

trait EngineObjectCenter[A] extends ObjectSynchronizer with PacketAttributesPresence {

    val center: PuppetCenter

    val defaultTreeViewBehavior: TreeViewBehavior

    def postObject[B <: A : ClassTag : TypeTag](id: Int, obj: B): B with PuppetWrapper[B]

    def postObject[B <: A : ClassTag : TypeTag](id: Int, obj: B, behavior: WrapperBehavior[B]): B with PuppetWrapper[B]

    def findObject[B <: A](id: Int): Option[B with PuppetWrapper[B]]

    def initAsWrapper[B <: A](puppet: B, info: PuppeteerInfo): B with PuppetWrapper[B]

    def getOrElse[U >: A](id: Int, orElse: => U): U = findObject(id).getOrElse(orElse)

    def isRegistered(id: Int): Boolean

}
