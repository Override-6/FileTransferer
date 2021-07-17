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

package fr.linkit.engine.connection.cache.repo.invokation.local

import fr.linkit.api.connection.cache.repo.PuppetWrapper
import fr.linkit.api.connection.cache.repo.description.RMIHandler
import fr.linkit.api.local.system.AppLogger

object SimpleRMIHandler extends RMIHandler {

    override def handleRMI[R](wrapper: PuppetWrapper[_])
                             (id: Int, defaultReturnValue: R)
                             (args: Array[Any])
                             (superCall: => R): R = {
        val methodBehavior = wrapper.getBehavior.getMethodBehavior(id).get
        val name           = methodBehavior.desc.javaMethod.getName
        val argsString     = args.mkString("(", ", ", ")")
        val puppeteer      = wrapper.getPuppeteer
        AppLogger.debug(s"$name: Performing rmi call for $name$argsString (id: $id)")
        AppLogger.debug(s"MethodBehavior = $methodBehavior")
        if (!methodBehavior.isRMIEnabled) {
            AppLogger.debug("The call is redirected to current object...")
            return superCall
        }
        // From here we are sure that we want to perform a remote
        // method invocation. (A Local invocation (super.xxx()) can be added).
        if (methodBehavior.invokeOnly) {
            AppLogger.debug("Invoke Only: Sending invocation request.")
            puppeteer.sendInvoke(id, args)
            var localResult: Any = defaultReturnValue
            if (methodBehavior.invocationKind.isLocalInvocationForced) {
                AppLogger.debug("The call is also redirected to current object...")
                localResult = superCall
            }
            AppLogger.debug("Returned local result = " + localResult)
            //# Note1: value of 'InvokeOnlyResult' can be "localResult", which will return the variable.
            //# Note2: Be aware that you can get a null value returned
            //#        if the 'InvokeOnlyResult' value of the annotated
            //#        method is set to "localResult" and the invocation
            //#        kind does not force local invocation.
            return localResult.asInstanceOf[R]
        }
        var result: R = defaultReturnValue
        if (methodBehavior.invocationKind.isLocalInvocationForced) {
            AppLogger.debug("The method invocation is redirected to current object...")
            result = superCall
            AppLogger.debug("Also performing asynchronous remote method invocation...")
            puppeteer.sendInvoke(id, args)
        } else {
            AppLogger.debug("Performing synchronous remote method invocation...")
            result = puppeteer.sendInvokeAndWaitResult[R](id, args)
        }
        result
    }
}
