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

package fr.linkit.api.connection.cache.repo.generation

import java.net.URLClassLoader
import java.nio.file.Path

class GeneratedClassLoader(val classRootFolder: Path, parent: ClassLoader, mates: Seq[ClassLoader]) extends URLClassLoader(Array(classRootFolder.toUri.toURL), parent) {

    override def loadClass(name: String, resolve: Boolean): Class[_] = {
        try {
            super.loadClass(name, resolve)
        } catch {
            case e: ClassNotFoundException => {
                var clazz: Class[_] = null
                var i               = 0
                while (clazz == null) {
                    try {
                        clazz = mates(i).loadClass(name)
                        i += 1
                    } catch {
                        case e: ClassNotFoundException =>
                            if (i > mates.length)
                                throw e
                    }
                }
                clazz
            }
        }
    }

}
