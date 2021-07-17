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

package fr.linkit.engine.connection.cache.repo.tree

import fr.linkit.api.connection.cache.repo.tree.{PuppetCenter, SyncNode}
import fr.linkit.engine.connection.cache.repo.DefaultEngineObjectCenter.PuppetProfile
import fr.linkit.engine.connection.cache.repo.{CacheRepoContent, NoSuchPuppetException}

import scala.collection.mutable

class DefaultPuppetCenter[A] extends PuppetCenter {

    private val puppets = new mutable.HashMap[Int, SyncNode[A]]

    override def getNode[B](path: Array[Int]): Option[SyncNode[B]] = puppets.get(path(0)) match {
        case None        => None
        case Some(value) if path.length > 1 => value
                .getGrandChild(path.dropRight(1))
                .map(_.asInstanceOf[SyncNode[B]])
        case s => s.asInstanceOf[Option[SyncNode[B]]]
    }

    override def addNode[B](path: Array[Int], provider: (Int, SyncNode[_]) => SyncNode[B]): Unit = {
        if (path.length == 1) {
            puppets.put(path.head, provider(path.head, null).asInstanceOf[SyncNode[A]])
            return
        }
        getNode[B](path.dropRight(1)).fold(throw new NoSuchPuppetException("Attempted to attach a puppet node to a parent that does not exists !")) {
            parent => parent.addChild(path.last, provider(path.last, _))
        }
    }

    override def snapshotContent: CacheRepoContent[A] = {
        def toProfile(node: SyncNode[_ <: A]): PuppetProfile[A] = {
            val puppeteer = node.puppeteer
            PuppetProfile[A](node.treeViewPath, puppeteer.getPuppetWrapper.detachedSnapshot(), puppeteer.puppeteerDescription.owner)
        }

        val array = puppets.values.map(toProfile).toArray
        new CacheRepoContent[A](array)
    }

}
