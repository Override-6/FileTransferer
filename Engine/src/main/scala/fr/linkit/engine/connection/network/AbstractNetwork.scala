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

package fr.linkit.engine.connection.network

import fr.linkit.api.connection.network.cache.{CacheOpenBehavior, SharedCacheManager}
import fr.linkit.api.connection.network.{Network, NetworkEntity}
import fr.linkit.api.connection.packet.Bundle
import fr.linkit.api.connection.{ConnectionContext, ExternalConnection}
import fr.linkit.api.local.system.AppLogger
import fr.linkit.engine.connection.network.cache.NetworkSharedCacheManager
import fr.linkit.engine.connection.network.cache.collection.{BoundedCollection, SharedCollection}
import fr.linkit.engine.connection.packet.traffic.ChannelScopes
import fr.linkit.engine.connection.packet.traffic.channel.SyncAsyncPacketChannel
import fr.linkit.engine.connection.packet.traffic.channel.request.RequestPacketChannel
import fr.linkit.engine.local.concurrency.pool.BusyWorkerPool.currentTasksId

import scala.collection.mutable

abstract class AbstractNetwork(override val connection: ConnectionContext) extends Network {

    private   val cacheRequestChannel                          = connection.getInjectable(12, ChannelScopes.discardCurrent, RequestPacketChannel)
    private   val caches                                       = mutable.HashMap.empty[String, NetworkSharedCacheManager]
    override  val globalCache       : SharedCacheManager       = initCaches()
    protected val sharedIdentifiers : SharedCollection[String] = globalCache.getCache(3, SharedCollection.set[String], CacheOpenBehavior.GET_OR_WAIT)
    protected val entityCommunicator: SyncAsyncPacketChannel   = connection.getInjectable(9, ChannelScopes.discardCurrent, SyncAsyncPacketChannel.busy)
    protected val entities: BoundedCollection.Immutable[NetworkEntity]

    override def listEntities: List[NetworkEntity] = {
        //println(s"entities = ${entities}")
        //println(s"sharedIdentifiers = ${sharedIdentifiers}")
        entities.toList
    }

    override def isConnected(identifier: String): Boolean = getEntity(identifier).isDefined

    override def getEntity(identifier: String): Option[NetworkEntity] = {
        if (entities != null)
            entities.find(_.identifier == identifier)
        else None
    }

    override def newCacheManager(family: String, owner: ConnectionContext): SharedCacheManager = {
        newCachesManager(family, owner.supportIdentifier)
    }

    protected def newCachesManager(family: String, owner: String): SharedCacheManager = {
        if (family == null || owner == null)
            throw new NullPointerException("Family or owner is null.")

        caches.get(family)
                .fold {
                    AppLogger.vDebug(s"$currentTasksId <> ${connection.supportIdentifier}: --> CREATING NEW SHARED CACHE MANAGER <$family, $owner>")
                    val cache = new NetworkSharedCacheManager(family, owner, connection, cacheRequestChannel)

                    //Will inject all packet that the new cache have possibly missed.
                    caches.synchronized {
                        AppLogger.vDebug(s"$currentTasksId <> ${connection.supportIdentifier}: PUTTING CACHE <$family, $owner> INTO CACHES")
                        caches.put(family, cache)
                        AppLogger.vDebug(s"$currentTasksId <> ${connection.supportIdentifier}: CACHES <$family, $owner> IS NOW : $caches")
                    }
                    cacheRequestChannel.injectStoredBundles()
                    cache: SharedCacheManager
                }(cache => {
                    AppLogger.vDebug(s"$currentTasksId <> ${connection.supportIdentifier}: <$family, $owner> UpDaTiNg CaChE")
                    cache.update()
                    cache
                })
    }

    override def getCacheManager(family: String): Option[SharedCacheManager] = {
        caches.get(family)
    }

    override def newCacheManager(family: String, owner: ExternalConnection): SharedCacheManager = {
        newCachesManager(family, owner.boundIdentifier)
    }

    protected def createEntity0(identifier: String, communicationChannel: SyncAsyncPacketChannel): NetworkEntity

    protected def createEntity(identifier: String): NetworkEntity = {
        if (identifier == connection.supportIdentifier) {
            return connectionEntity
        }

        val channel = entityCommunicator.subInjectable(Array(identifier), SyncAsyncPacketChannel.busy, transparent = true)
        val ent     = createEntity0(identifier, channel)
        ent
    }

    private def initCaches(): SharedCacheManager = {
        connection.translator.initNetwork(this)

        def findCacheToNotify(bundle: Bundle)
                             (notifyAction: NetworkSharedCacheManager => Unit): Unit = {
            val attr = bundle.attributes
            attr.getAttribute[String]("family") match {
                case Some(family) =>
                    val opt = caches.synchronized {
                        AppLogger.vWarn(s"$currentTasksId <> ${connection.supportIdentifier}: FINDING CACHE '$family' FOR PACKET ${bundle.packet} into $caches")
                        caches.get(family)
                    }
                    opt
                            //If cache does not contains the family tag, this mean that it could possibly be
                            //opened in the future, so received packets will be stored and reInjected every
                            //time a cache opens.
                            .fold(bundle.store())(cache => {
                                notifyAction(cache)
                            })
            }
        }

        cacheRequestChannel.addRequestListener(bundle => {
            AppLogger.vDebug(s"Request body: ${bundle}")
            findCacheToNotify(bundle) {
                _.handleRequest(bundle)
            }
        })

        newCachesManager(s"Global Cache", serverIdentifier)
    }

}
