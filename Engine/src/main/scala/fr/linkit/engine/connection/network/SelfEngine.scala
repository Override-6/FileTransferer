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

import fr.linkit.api.connection.ConnectionContext
import fr.linkit.api.connection.cache.SharedCacheManager
import fr.linkit.api.connection.network.{Engine, ExternalConnectionState, Network, StaticAccessor}
import fr.linkit.api.local.system.{AppLogger, Versions}
import fr.linkit.engine.local.system.StaticVersions

import java.sql.Timestamp

class SelfEngine(connection: ConnectionContext,
                 state: => ExternalConnectionState,
                 override val cache: SharedCacheManager) extends Engine {

    override val identifier: String = connection.currentIdentifier

    override val connectionDate: Timestamp = new Timestamp(System.currentTimeMillis())

    override val network: Network = connection.network

    override val versions: Versions = StaticVersions.currentVersions

    update()

    override def update(): this.type = {
        cache.postInstance(2, connectionDate)
        cache.postInstance(3, versions)
        cache.update()
        this
    }

    override def getConnectionState: ExternalConnectionState = state

    override def toString: String = s"SelfNetworkEngine(identifier: ${identifier})"

    override val staticAccessor: StaticAccessor = null
}
