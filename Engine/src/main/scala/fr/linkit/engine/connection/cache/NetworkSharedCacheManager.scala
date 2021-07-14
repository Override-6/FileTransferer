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

package fr.linkit.engine.connection.cache

import fr.linkit.api.connection.cache._
import fr.linkit.api.connection.network.Network
import fr.linkit.api.connection.packet.channel.ChannelScope
import fr.linkit.api.connection.packet.traffic.PacketInjectableContainer
import fr.linkit.api.local.concurrency.WorkerPools.currentTasksId
import fr.linkit.api.local.concurrency.{WorkerPools, workerExecution}
import fr.linkit.api.local.system.AppLogger
import fr.linkit.engine.connection.cache.NetworkSharedCacheManager.MockCache
import fr.linkit.engine.connection.cache.map.SharedMap
import fr.linkit.engine.connection.packet.fundamental.RefPacket
import fr.linkit.engine.connection.packet.fundamental.ValPacket.IntPacket
import fr.linkit.engine.connection.packet.traffic.ChannelScopes
import fr.linkit.engine.connection.packet.traffic.channel.request.{RequestBundle, RequestPacketChannel}

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

class NetworkSharedCacheManager(override val family: String,
                                override val ownerID: String,
                                override val network: Network,
                                container: PacketInjectableContainer,
                                requestChannel: RequestPacketChannel) extends SharedCacheManager {

    private      val ownerScope                                   = prepareOwnerScope()
    private lazy val quickCache: map.SharedMap[Int, Serializable] = init()

    override def postInstance[A <: Serializable](key: Int, value: A): value.type = {
        quickCache.put(key, value)
        value
    }

    override def findInstance[A <: Serializable](key: Int): Option[A] = {
        quickCache.get(key).asInstanceOf[Option[A]]
    }

    override def getInstanceOrWait[A <: Serializable](key: Int): A = {
        val obj = quickCache.getOrWait(key)
        obj.asInstanceOf[A]
    }

    override def apply[A <: Serializable](key: Int): A = quickCache(key).asInstanceOf[A]

    override def getCache[A <: SharedCache : ClassTag](cacheID: Int, factory: SharedCacheFactory[A with InternalSharedCache], behavior: CacheSearchBehavior): A = {
        getCache0[A](cacheID, factory, behavior) { retrieveCache: (() => Unit) => retrieveCache.apply() }
    }

    @workerExecution
    override def getCacheAsync[A <: SharedCache : ClassTag](cacheID: Int, factory: SharedCacheFactory[A with InternalSharedCache], behavior: CacheSearchBehavior): A = {
        getCache0[A](cacheID, factory, behavior)((retrieveCache: () => Unit) =>
            WorkerPools.ensureCurrentIsWorker("Please use getCache instead.").runLater(retrieveCache.apply())
        )
    }

    override def retrieveCacheContent(cacheID: Int, behavior: CacheSearchBehavior): Option[CacheContent] = {
        println(s"Sending request to $ownerID in order to retrieve content of cache number $cacheID")
        val request = requestChannel
                .makeRequest(ownerScope)
                .putAttribute("behavior", behavior)
                .addPacket(IntPacket(cacheID))
                .submit()

        try {
            val response         = request.nextResponse
            val possibleErrorMsg = response.getAttribute[String]("errorMsg")

            if (possibleErrorMsg.isDefined) {
                val errorMsg = possibleErrorMsg.get
                throw new CacheOpenException(errorMsg + s"(this = $ownerID)")
            }

            val content = response.nextPacket[RefPacket[Option[CacheContent]]].value
            request.detach()

            println(s"Content '$cacheID' received ! ($content)")
            content
        } catch {
            case e: Throwable =>
                AppLogger.fatal(s"Was executing request (${request.id}) for cache ID '$cacheID'.")
                AppLogger.printStackTrace(e)
                System.exit(1)
                throw null
        }
    }

    override def update(): this.type = {
        println("Cache will be updated.")
        LocalCacheHandler.updateAll()
        //quickCache will be updated by LocalCacheHandler.updateAll call
        this
    }

    def forget(cacheID: Int): Unit = {
        LocalCacheHandler.unregister(cacheID)
    }

    def handleRequest(requestBundle: RequestBundle): Unit = {
        val request  = requestBundle.packet
        val response = requestBundle.responseSubmitter
        val coords   = requestBundle.coords
        println(s"HANDLING REQUEST $request, $coords")

        val senderID: String = coords.senderID
        val behavior         = request.getAttribute[CacheSearchBehavior]("behavior").get
        val cacheID          = request.nextPacket[IntPacket].value

        println(s"RECEIVED CONTENT REQUEST FOR IDENTIFIER $cacheID REQUESTOR : $senderID")
        println(s"Behavior = $behavior")
        val contentOpt = LocalCacheHandler.getContent(cacheID)
        if (contentOpt.isDefined) {
            response.addPacket(RefPacket[Option[CacheContent]](contentOpt))
        } else {
            import CacheSearchBehavior._

            behavior match {
                case GET_OR_CRASH =>
                    val msg = s"Requested cache of identifier '$cacheID' is not opened or isn't handled by this connection."
                    response.putAttribute("errorMsg", msg)
                case GET_OR_WAIT  =>
                    //If the requester is not the owner, wait the owner to open the cache.
                    if (senderID != ownerID) {
                        requestChannel.storeBundle(requestBundle)
                        println(s"Await open ($cacheID)...")
                        return
                    }
                    //The sender is the owner : this class must create the cache content.
                    response.addPacket(RefPacket[Option[CacheContent]](None))
                case GET_OR_OPEN  =>
                    response.addPacket(RefPacket[Option[CacheContent]](None))
            }
        }
        println(s"Content Response for cache $cacheID = $response.")
        response.submit()
    }

    private def getCache0[A <: SharedCache : ClassTag](cacheID: Int, factory: SharedCacheFactory[A with InternalSharedCache], behavior: CacheSearchBehavior)(cacheRetrievalAction: (() => Unit) => Unit): A = {
        LocalCacheHandler
                .findCache[A](cacheID)
                .getOrElse {
                    val sharedCache = factory.createNew(this, cacheID, container)

                    cacheRetrievalAction { () =>
                        println(s"OPENING CACHE $cacheID OF TYPE ${classTag[A].runtimeClass}")
                        val baseContent = retrieveCacheContent(cacheID, behavior)
                        println(s"CONTENT RECEIVED (${baseContent}) FOR CACHE $cacheID")

                        if (baseContent.isDefined) {
                            sharedCache.setContent(baseContent.get)
                        }
                    }

                    LocalCacheHandler.register(cacheID, sharedCache)
                    requestChannel.injectStoredBundles()
                    sharedCache
                }
    }

    private def init(): SharedMap[Int, Serializable] = {
        /*
        * Don't touch, scala objects works as a lazy val, and all lazy val are synchronized on the instance that
        * they are computing. If you remove this line, NetworkSCManager could some times be deadlocked because retrieveCacheContent
        * will wait for its content's request, and thus its request response will be handled by another thread,
        * which will need LocalCacheHandler in order to retrieve the local cache, which is synchronized, so it will
        * be blocked until the thread that requested the content get it's response, but it's impossible because the thread
        * that handles the request is waiting...
        * For simple, if you remove this line, a deadlock will occur.
        * */
        LocalCacheHandler.loadClass()
        val content       = retrieveCacheContent(1, CacheSearchBehavior.GET_OR_WAIT)
        val sharedObjects = SharedMap[Int, Serializable].createNew(this, 1, container)
        if (content.isDefined) {
            sharedObjects.setContent(content.get)
        }

        LocalCacheHandler.register(1, sharedObjects)
        sharedObjects.foreachKeys(LocalCacheHandler.registerMock) //mock all current caches that are registered on this family
        println("Shared objects : " + sharedObjects)
        sharedObjects
    }

    private def prepareOwnerScope(): ChannelScope = {
        val traffic = requestChannel.traffic
        val writer  = traffic.newWriter(requestChannel.identifier)
        val scope   = ChannelScopes.retains(ownerID).apply(writer)
        scope.addDefaultAttribute("family", family)
        scope
    }

    protected object LocalCacheHandler {

        private val localRegisteredCaches = mutable.Map.empty[Int, InternalSharedCache]

        def updateAll(): Unit = {
            println(s"updating cache ($localRegisteredCaches)...")
            localRegisteredCaches
                    .foreach(_._2.update())
            println(s"cache updated ! ($localRegisteredCaches)")
        }

        def register(identifier: Int, cache: InternalSharedCache): Unit = {
            println(s"Registering $identifier into local cache.")
            localRegisteredCaches.put(identifier, cache)
            println(s"Local cache is now $localRegisteredCaches")
        }

        def unregister(identifier: Int): Unit = {
            println(s"Removing cache $identifier")
            localRegisteredCaches.remove(identifier)
            println(s"Cache is now $identifier")
        }

        def registerMock(identifier: Int): Unit = {
            localRegisteredCaches.put(identifier, MockCache)
        }

        def getContent(cacheID: Int): Option[CacheContent] = {
            localRegisteredCaches.get(cacheID).map(_.snapshotContent)
        }

        def isRegistered(cacheID: Int): Boolean = {
            localRegisteredCaches.contains(cacheID)
        }

        def findCache[A: ClassTag](cacheID: Int): Option[A] = {
            val opt = localRegisteredCaches.get(cacheID).asInstanceOf[Option[A]]
            if (opt.exists(_.isInstanceOf[MockCache.type]))
                return None

            if (opt.exists(!_.isInstanceOf[A])) {
                val requestedClass = classTag[A].runtimeClass
                val presentClass   = opt.get.getClass
                throw new IllegalArgumentException(s"Attempted to open a cache of type '$cacheID' while a cache with the same id is already registered, but does not have the same type. ($presentClass vs $requestedClass)")
            }

            opt
        }

        override def toString: String = localRegisteredCaches.toString()

        def loadClass(): Unit = ()

    }

    private def println(msg: => String): Unit = {
        AppLogger.trace(s"$currentTasksId <> <$family, $ownerID> $msg")
    }

}

object NetworkSharedCacheManager {

    object MockCache extends InternalSharedCache {

        override val family: String = ""

        override def snapshotContent: CacheContent = CacheArrayContent(Array())

        override def setContent(cacheContent: CacheContent): Unit = ()

        override def update(): this.type = this
    }

}