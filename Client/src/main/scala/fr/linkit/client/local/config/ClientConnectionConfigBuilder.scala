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

package fr.linkit.client.local.config

import fr.linkit.api.connection.packet.serialization.PacketTranslator
import fr.linkit.api.local.system.security.BytesHasher
import fr.linkit.engine.connection.packet.serialization.DefaultPacketTranslator

import java.net.{InetSocketAddress, Socket}

abstract class ClientConnectionConfigBuilder {

    var reconnectionMillis: Int                         = 5000
    var socketFactory     : InetSocketAddress => Socket = s => new Socket(s.getAddress, s.getPort)
    var configName        : String                      = "simple-config"
    var hasher            : BytesHasher                 = BytesHasher.inactive
    var translator        : PacketTranslator            = new DefaultPacketTranslator
    val identifier   : String
    val remoteAddress: InetSocketAddress

    /**
     * @return a [[ClientConnectionConfiguration]] based on the current settings
     * */
    def buildConfig(): ClientConnectionConfiguration = {
        val builder = this
        new ClientConnectionConfiguration {
            override val reconnectionMillis: Int                         = builder.reconnectionMillis
            override val socketFactory     : InetSocketAddress => Socket = builder.socketFactory
            override val remoteAddress     : InetSocketAddress           = builder.remoteAddress
            override val configName        : String                      = builder.configName
            override val identifier        : String                      = builder.identifier
            override val hasher            : BytesHasher                 = builder.hasher
            override val translator        : PacketTranslator            = builder.translator
        }: ClientConnectionConfiguration
    }

}

object ClientConnectionConfigBuilder {

    implicit def autoBuild(builder: ClientConnectionConfigBuilder): ClientConnectionConfiguration = {
        builder.buildConfig()
    }

}