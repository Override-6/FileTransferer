/*
 * Copyright (c) 2021. Linkit and or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can only use it for personal uses, studies or documentation.
 * You can download this source code, and modify it ONLY FOR PERSONAL USE and you
 * ARE NOT ALLOWED to distribute your MODIFIED VERSION.
 *
 * Please contact maximebatista18@gmail.com if you need additional information or have any
 * questions.
 */

package fr.`override`.linkit.server.security

import fr.`override`.linkit.api.Relay
import fr.`override`.linkit.api.system.security.RelaySecurityManager
import fr.`override`.linkit.server.connection.ClientConnection

trait RelayServerSecurityManager extends RelaySecurityManager {

    def canConnect(connection: ClientConnection): Boolean

}

object RelayServerSecurityManager {

    class Default extends RelayServerSecurityManager{
        override def canConnect(connection: ClientConnection): Boolean = true

        override def hashBytes(raw: Array[Byte]): Array[Byte] = raw

        override def deHashBytes(hashed: Array[Byte]): Array[Byte] = hashed

        override def checkRelay(relay: Relay): Unit = ()
    }

    def default(): RelayServerSecurityManager = new Default

}
