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

package fr.linkit.client.connection

import fr.linkit.api.connection.packet.serialization.PacketTranslator
import fr.linkit.api.connection.packet.traffic.PacketTraffic
import fr.linkit.api.connection.packet.traffic.PacketTraffic.SystemChannelID
import fr.linkit.api.local.system.event.EventNotifier
import fr.linkit.client.ClientApplication
import fr.linkit.client.local.config.ClientConnectionConfiguration
import fr.linkit.engine.connection.packet.serialization.DefaultPacketTranslator
import fr.linkit.engine.connection.packet.traffic.{ChannelScopes, DynamicSocket, SocketPacketTraffic}
import fr.linkit.engine.local.concurrency.PacketReaderThread
import fr.linkit.engine.local.system.SystemPacketChannel
import fr.linkit.engine.local.system.event.DefaultEventNotifier

case class ClientConnectionSession(socket: DynamicSocket,
                                   info: ClientConnectionSessionInfo,
                                   serverIdentifier: String) {

    val appContext       : ClientApplication             = info.appContext
    val configuration    : ClientConnectionConfiguration = info.configuration
    val readThread       : PacketReaderThread            = info.readThread
    val currentIdentifier: String                        = configuration.identifier
    val translator       : PacketTranslator              = configuration.translator
    val traffic          : PacketTraffic                 = new SocketPacketTraffic(socket, translator, appContext, currentIdentifier, serverIdentifier)
    val eventNotifier    : EventNotifier                 = new DefaultEventNotifier
    val systemChannel    : SystemPacketChannel           = traffic.getInjectable(SystemChannelID, ChannelScopes.discardCurrent, SystemPacketChannel)

}
