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

package fr.linkit.engine.test

import fr.linkit.engine.local.utils.ScalaUtils
import org.junit.jupiter.api.Test

import scala.collection.mutable.ListBuffer
import scala.reflect.runtime.universe

class ScalaReflectionTest {

    @Test
    def makeTests(): Unit = {
        val v = ScalaUtils.allocate[ListBuffer[_]](classOf[ListBuffer[_]])
        println("OK")
        println(s"v = ${v}")

    }

    private def getTypeFlag[T: universe.TypeTag]: universe.TypeTag[T] = universe.typeTag[T]

}
