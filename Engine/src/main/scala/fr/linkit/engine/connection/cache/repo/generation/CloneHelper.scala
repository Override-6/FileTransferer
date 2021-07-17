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

package fr.linkit.engine.connection.cache.repo.generation

import fr.linkit.api.connection.cache.repo.PuppetWrapper
import fr.linkit.engine.local.utils.ScalaUtils.{allocate, pasteAllFields}

object CloneHelper {

    def instantiateFromOrigin[A](wrapperClass: Class[A with PuppetWrapper[A]], origin: A): A with PuppetWrapper[A] = {
        val instance  = allocate[A with PuppetWrapper[A]](wrapperClass)
        pasteAllFields(instance, origin)
        instance
    }

    def clone[A](origin: A): A = {
        val instance = allocate[A](origin.getClass)
        pasteAllFields(instance, origin)
        instance
    }

    def detachedWrapperClone[A](origin: PuppetWrapper[A]): A = {
        val instance = allocate[A](origin.getWrappedClass)
        pasteAllFields(instance, origin)
        instance
    }

    def prepareClass(clazz: Class[_]): Unit = {
        clazz.getFields
        clazz.getDeclaredFields
        clazz.getMethods
        clazz.getDeclaredMethods
        clazz.getSimpleName
        clazz.getName
        clazz.getAnnotations
        clazz.getDeclaredAnnotations
        clazz.getConstructors
        clazz.getDeclaredConstructors
    }

}
