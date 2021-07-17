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

package fr.linkit.engine.connection.cache.repo

import fr.linkit.api.connection.cache.repo.description.{PuppeteerInfo, WrapperBehavior}
import fr.linkit.api.connection.cache.repo.{InvocationChoreographer, PuppetWrapper, Puppeteer}

class NonSynchronizedObjectWrapper(obj: AnyRef) extends PuppetWrapper[Any] {

    override def initPuppeteer(puppeteer: Puppeteer[Any]): Unit = ()

    override def getPuppeteer: Puppeteer[Any] = fail()

    override def getBehavior: WrapperBehavior[Any] = fail()

    override def getPuppeteerInfo: PuppeteerInfo = fail()

    override def getChoreographer: InvocationChoreographer = fail()

    override def isInitialized: Boolean = false

    override def asWrapped(): Any = obj

    override def detachedSnapshot(): Any = obj

    override def getWrappedClass: Class[Any] = obj.getClass.asInstanceOf[Class[Any]]

    private def fail(): Nothing = {
        throw new IllegalStateException("This object is not synchronizable.")
    }

}
