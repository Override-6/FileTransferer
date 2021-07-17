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

package fr.linkit.engine.connection.packet.traffic

import fr.linkit.api.connection.packet.serialization.PacketTranslator
import fr.linkit.api.connection.packet.traffic.PacketWriter
import fr.linkit.api.local.concurrency.Procrastinator
import org.jetbrains.annotations.NotNull

class SocketPacketTraffic(@NotNull socket: DynamicSocket,
                          @NotNull translator: PacketTranslator,
                          @NotNull procrastinator: Procrastinator,
                          @NotNull override val currentIdentifier: String,
                          @NotNull override val serverIdentifier: String) extends AbstractPacketTraffic(currentIdentifier, procrastinator) {

    override def newWriter(identifier: Int): PacketWriter = {
        new SocketPacketWriter(socket, translator, WriterInfo(this, identifier))
    }

}
