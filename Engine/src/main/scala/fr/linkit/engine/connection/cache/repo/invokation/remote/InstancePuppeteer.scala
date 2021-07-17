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

package fr.linkit.engine.connection.cache.repo.invokation.remote

import fr.linkit.api.connection.cache.repo._
import fr.linkit.api.connection.cache.repo.description.annotation.InvocationKind
import fr.linkit.api.connection.cache.repo.description.{MethodBehavior, PuppeteerInfo, WrapperBehavior}
import fr.linkit.api.connection.packet.channel.ChannelScope
import fr.linkit.api.connection.packet.channel.ChannelScope.ScopeFactory
import fr.linkit.api.local.system.AppLogger
import fr.linkit.engine.connection.cache.repo.invokation.local.ObjectChip
import fr.linkit.engine.connection.cache.repo.tree.PuppetNode
import fr.linkit.engine.connection.cache.repo.{NoSuchPuppetException, ThrowableWrapper}
import fr.linkit.engine.connection.packet.fundamental.RefPacket
import fr.linkit.engine.connection.packet.traffic.ChannelScopes
import fr.linkit.engine.connection.packet.traffic.channel.request.RequestPacketChannel

import java.util.concurrent.ThreadLocalRandom

class InstancePuppeteer[S](channel: RequestPacketChannel,
                           override val repo: EngineObjectCenter[_],
                           override val puppeteerInfo: PuppeteerInfo,
                           val wrapperBehavior: WrapperBehavior[S]) extends Puppeteer[S] {

    override val ownerID     : String                  = puppeteerInfo.owner
    private  val bcScope                               = prepareScope(ChannelScopes.discardCurrent)
    private  val ownerScope                            = prepareScope(ChannelScopes.retains(ownerID))
    private var puppetWrapper: S with PuppetWrapper[S] = _

    override def isCurrentEngineOwner: Boolean = ownerID == channel.traffic.currentIdentifier

    override def getPuppetWrapper: S with PuppetWrapper[S] = puppetWrapper

    override def sendInvokeAndWaitResult[R](methodId: Int, args: Array[Any]): R = {
        val bhv = wrapperBehavior.getMethodBehavior(methodId).getOrElse {
            throw new NoSuchMethodException(s"Remote method not found for id '$methodId'")
        }

        AppLogger.debug(s"Remotely invoking method ${bhv.desc.symbol.name}")
        val treeViewPath = puppeteerInfo.treeViewPath
        val result       = channel.makeRequest(chooseScope(bhv.invocationKind))
                .addPacket(InvocationPacket(treeViewPath, methodId, args, null))//TODO
                .submit()
                .nextResponse
                .nextPacket[RefPacket[R]].value
        result match {
            //FIXME ambiguity with broadcast method invocation.
            case ThrowableWrapper(e) => throw new RemoteInvocationFailedException(s"Invocation of method $methodId with arguments '${args.mkString(", ")}' failed.", e)
            case result              => result
        }
    }

    override def sendInvoke(methodId: Int, args: Array[Any]): Unit = {
        val bhv = wrapperBehavior.getMethodBehavior(methodId).getOrElse {
            throw new NoSuchMethodException(s"Remote method not found for id '$methodId'")
        }
        AppLogger.debug(s"Remotely invoking method ${bhv.desc.symbol.name}(${args.mkString(",")})")
        channel.makeRequest(chooseScope(bhv.invocationKind))
                .addPacket(InvocationPacket(puppeteerInfo.treeViewPath, methodId, args, null))//TODO
                .submit()
                .detach()
    }

    override def init(wrapper: S with PuppetWrapper[S]): Unit = {
        if (this.puppetWrapper != null) {
            throw new IllegalStateException("This Puppeteer already controls a puppet instance !")
        }
        this.puppetWrapper = wrapper
    }


    override def synchronizedObj(obj: Any, id: Int = ThreadLocalRandom.current().nextInt()): Any = {
        val currentPath = puppeteerInfo.treeViewPath
        val objPath     = currentPath ++ Array(id)
        val defaults    = repo.defaultTreeViewBehavior
        repo.genSynchronizedObject(objPath, obj, ownerID, defaults)
    }

    private def chooseScope(kind: InvocationKind): ChannelScope = {
        import InvocationKind._
        kind match {
            case ONLY_OWNER | LOCAL_AND_OWNER     => ownerScope
            case ONLY_REMOTES | LOCAL_AND_REMOTES => bcScope
            case ONLY_LOCAL                       =>
                throw new UnsupportedOperationException(s"Unable to perform a remote invocation with Invocation kind of type $ONLY_LOCAL")
        }
    }

    private def prepareScope(factory: ScopeFactory[_ <: ChannelScope]): ChannelScope = {
        if (channel == null)
            return null
        val writer = channel.traffic.newWriter(channel.identifier)
        val scope  = factory.apply(writer)
        repo.drainAllDefaultAttributes(scope)
        scope
    }

}
