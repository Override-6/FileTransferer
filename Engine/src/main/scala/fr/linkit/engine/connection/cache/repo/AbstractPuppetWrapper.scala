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

import fr.linkit.api.connection.cache.repo.description.{MethodBehavior, PuppeteerInfo, WrapperBehavior}
import fr.linkit.api.connection.cache.repo.{InvocationChoreographer, PuppetWrapper, Puppeteer}
import fr.linkit.engine.connection.cache.repo.generation.CloneHelper

trait AbstractPuppetWrapper[A] extends PuppetWrapper[A] {

    @transient protected var puppeteer           : Puppeteer[A]            = _
    @transient protected var behavior            : WrapperBehavior[A]      = _
    @transient protected var choreographer       : InvocationChoreographer = _
    protected            var puppeteerDescription: PuppeteerInfo           = _

    def wrappedClass: Class[_]

    override def initPuppeteer(puppeteer: Puppeteer[A]): Unit = {
        if (this.behavior != null)
            throw new PuppetAlreadyInitialisedException("This puppet is already initialized !")
        this.puppeteer = puppeteer
        this.puppeteerDescription = puppeteer.puppeteerDescription
        this.behavior = puppeteer.wrapperBehavior
        this.puppeteer.init(asWrapper)
        this.choreographer = new InvocationChoreographer()
    }

    @inline override def isInitialized: Boolean = puppeteer != null

    override def detachedSnapshot: A = {
        CloneHelper.detachedWrapperClone(this)
    }

    override def getWrappedClass: Class[A] = wrappedClass.asInstanceOf[Class[A]]

    override def asWrapped(): A = asWrapper

    override def getChoreographer: InvocationChoreographer = choreographer

    override def getPuppeteer: Puppeteer[A] = puppeteer

    override def getBehavior: WrapperBehavior[A] = behavior

    override def getPuppeteerInfo: PuppeteerInfo = puppeteerDescription

    private def synchronizedParams(bhv: MethodBehavior, objects: Array[Any]): Array[Any] = {
        var i = -1
        objects.map(obj => {
            i += 1
            if (!bhv.synchronizedParams(i) || obj.isInstanceOf[PuppetWrapper[_]])
                obj
            else puppeteer.synchronizedObj(obj)
        })
    }

    protected def handleCall[R](id: Int, defaultReturnValue: R)
                               (args: Array[Any])(superCall: Array[Any] => Any): R = {
        val methodBehavior = behavior.getMethodBehavior(id).get
        val name           = methodBehavior.desc.javaMethod.getName
        val argsString     = args.mkString("(", ", ", ")")
        val synchronizedArgs = synchronizedParams(methodBehavior, args)
        /*if (name == "toString")
            Thread.dumpStack()*/
        if (choreographer.isMethodExecutionForcedToLocal) {
            println(s"forced local method call $name$argsString.")
            return superCall(synchronizedArgs).asInstanceOf[R]
        }
        methodBehavior.handler
                .handleRMI[R](this)(id, defaultReturnValue)(synchronizedArgs)(performSuperCall[R](superCall(synchronizedArgs)))
    }

    private def asWrapper: A with PuppetWrapper[A] = this.asInstanceOf[A with PuppetWrapper[A]]

    @inline private def performSuperCall[R](@inline superCall: => Any): R = {
        choreographer.forceLocalInvocation[R] {
            superCall.asInstanceOf[R]
        }
    }
}
