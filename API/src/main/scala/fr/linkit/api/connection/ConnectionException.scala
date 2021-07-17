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

package fr.linkit.api.connection

import fr.linkit.api.local.system.AppException
import org.jetbrains.annotations.Nullable

class ConnectionException(@Nullable connection: ConnectionContext,
                          msg: String, cause: Throwable = null) extends AppException(msg, cause) {

    override def appendMessage(sb: StringBuilder): Unit = {
        super.appendMessage(sb)
        if (connection == null)
            return

        val identifier = connection.currentIdentifier
        sb.append("An exception occurred with connection '")
                .append(identifier)
                .append('\'')
        connection match {
            case external: ExternalConnection =>
                sb.append("\n\t")
                        .append("bound to '")
                        .append(external.boundIdentifier)
                        .append("':\n")
        }
    }

}

object ConnectionException {

    def apply(@Nullable connection: ConnectionContext, @Nullable msg: String, @Nullable cause: Throwable = null): ConnectionException = {
        new ConnectionException(connection, msg, cause)
    }

}
