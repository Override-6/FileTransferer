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

package fr.linkit.api.connection.cache.repo

import fr.linkit.api.connection.cache.repo.description.{PuppeteerInfo, WrapperBehavior}

import java.util.concurrent.ThreadLocalRandom

trait Puppeteer[S] {

    val ownerID: String

    val repo: EngineObjectCenter[_]

    val puppeteerDescription: PuppeteerInfo

    val wrapperBehavior: WrapperBehavior[S]

    def isCurrentEngineOwner: Boolean

    def getPuppetWrapper: S with PuppetWrapper[S]

    def sendInvokeAndWaitResult[R](methodId: Int, args: Array[Any]): R

    def init(wrapper: S with PuppetWrapper[S]): Unit

    def sendInvoke(methodId: Int, args: Array[Any]): Unit

    def synchronizedObj(obj: Any, id: Int = ThreadLocalRandom.current().nextInt()): Any
}
