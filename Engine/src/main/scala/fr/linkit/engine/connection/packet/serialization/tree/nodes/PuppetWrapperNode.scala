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

package fr.linkit.engine.connection.packet.serialization.tree.nodes

import fr.linkit.api.connection.cache.repo.PuppetWrapper
import fr.linkit.api.connection.cache.repo.description.PuppeteerInfo
import fr.linkit.api.connection.network.Network
import fr.linkit.api.connection.packet.serialization.tree._
import fr.linkit.api.local.system.AppLogger
import fr.linkit.engine.connection.cache.repo.{DefaultEngineObjectCenter, NonSynchronizedObjectWrapper}
import fr.linkit.engine.local.utils.NumberSerializer

class PuppetWrapperNode(network: Network) extends NodeFactory[PuppetWrapper[_]] {

    private val WrapperFlag = -114: Byte

    override def canHandle(clazz: Class[_]): Boolean = classOf[PuppetWrapper[_]].isAssignableFrom(clazz)

    override def canHandle(info: ByteSeq): Boolean = info.isClassDefined && info.sameFlagAt(4, WrapperFlag)

    override def newNode(finder: NodeFinder, profile: ClassProfile[PuppetWrapper[_]]): SerialNode[PuppetWrapper[_]] = {
        (wrapper, putTypeHint) => {
            val detached  = DetachedWrapper(wrapper.detachedSnapshot(), wrapper.getPuppeteerInfo)
            val classCode = NumberSerializer.serializeInt(detached.getClass.getName.hashCode)
            val bytes     = finder.getSerialNodeForRef(detached)
                    .serialize(detached, true)
            val result    = classCode ++ Array(WrapperFlag) ++ bytes
            result
        }
    }

    override def newNode(finder: NodeFinder, bytes: ByteSeq): DeserialNode[PuppetWrapper[_]] = {
        () => {
            deserializeWrapper(finder, bytes)
        }
    }

    private def deserializeWrapper(finder: NodeFinder, bytes: ByteSeq): PuppetWrapper[_] = {
        val detachedWrapper = finder.getDeserialNodeFor(bytes.array.drop(5)).deserialize().asInstanceOf[DetachedWrapper]
        val wrapped         = detachedWrapper.detached
        val info            = detachedWrapper.puppeteerInfo
        val family          = info.cacheFamily
        val opt             = network.getCacheManager(family)
        if (opt.isEmpty) {
            AppLogger.error(s"Could not synchronize Wrapper object ${wrapped.getClass} because no cache of family $family. The object is returned as null.")
            return null
        }
        opt.get
                .getCacheAsync(info.cacheID, DefaultEngineObjectCenter[Any]())
                .initAsWrapper(wrapped, info)
    }

    case class DetachedWrapper(detached: Any, puppeteerInfo: PuppeteerInfo)
}
