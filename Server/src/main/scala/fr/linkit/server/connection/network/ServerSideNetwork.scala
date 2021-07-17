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

package fr.linkit.server.connection.network

import fr.linkit.api.connection.cache.CacheSearchBehavior
import fr.linkit.api.connection.network.{Engine, ExternalConnectionState}
import fr.linkit.api.connection.packet.traffic.PacketTraffic
import fr.linkit.engine.connection.cache.SharedInstance
import fr.linkit.engine.connection.cache.collection.{BoundedCollection, CollectionModification}
import fr.linkit.engine.connection.network.{AbstractNetwork, SelfEngine}
import fr.linkit.engine.connection.packet.traffic.channel.SyncAsyncPacketChannel
import fr.linkit.server.connection.{ServerConnection, ServerExternalConnection}

import java.sql.Timestamp

class ServerSideNetwork(serverConnection: ServerConnection)(implicit traffic: PacketTraffic)
        extends AbstractNetwork(serverConnection) {

    override           val connectionEngine: Engine                              = createServerEntity()
    override protected val entities        : BoundedCollection.Immutable[Engine] = {
        sharedIdentifiers
                //.addListener((_, _: Int, _) => if (entities != null) println("entities are now : " + entities)) //debug purposes
                .add(serverIdentifier)
                .mapped(createEntity)
                .addListener(handleTraffic)
    }

    override val startUpDate: Timestamp = cache.postInstance(2, new Timestamp(System.currentTimeMillis()))

    override def serverIdentifier: String = serverConnection.currentIdentifier

    //The current connection is the network's server connection.
    override def serverEngine: Engine = connectionEngine

    override def createEngine(identifier: String, communicator: SyncAsyncPacketChannel): Engine = {
        val entityCache = newCachesManager(identifier, identifier)
        val v           = new ExternalConnectionEngine(serverConnection, identifier, entityCache)
        v
    }

    def removeEntity(identifier: String): Unit = {
        getEngine(identifier)
                .foreach(entity => {
                    if (entity.getConnectionState != ExternalConnectionState.CLOSED)
                        throw new IllegalStateException(s"Could not remove entity '$identifier' from network as long as it still open")
                    sharedIdentifiers.remove(identifier)
                })
    }

    def createServerEntity(): Engine = {
        val selfCache    = newCacheManager(serverIdentifier, serverConnection)
        val serverEntity = new SelfEngine(serverConnection, ExternalConnectionState.CONNECTED, selfCache) //Server always connected to himself
        serverEntity
                .cache
                .getCache(3, SharedInstance[ExternalConnectionState], CacheSearchBehavior.GET_OR_WAIT)
                .set(ExternalConnectionState.CONNECTED) //technically always connected
        serverEntity
    }

    private def handleTraffic(mod: CollectionModification, index: Int, entityOpt: Option[Engine]): Unit = {
        /*lazy val entity = entityOpt.orNull //get
        //println(s"mod = ${mod}")
        //println(s"index = ${index}")
        //println(s"entity = ${entity}")

        import CollectionModification._
        val event = mod match {
            case ADD => NetworkEvents.entityAdded(entity)
            case REMOVE => NetworkEvents.entityRemoved(entity)
            case _ => return
        }
        //server.eventNotifier.notifyEvent(server.networkHooks, event)
        */
    }
}
