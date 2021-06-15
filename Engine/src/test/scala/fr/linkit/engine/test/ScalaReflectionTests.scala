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

import fr.linkit.api.local.resource.external.{ResourceFile, ResourceFolder}
import fr.linkit.engine.test.ScalaReflectionTests.TestClass
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{Test, TestInstance}

import scala.reflect.runtime.universe._

@TestInstance(Lifecycle.PER_CLASS)
class ScalaReflectionTests {

    @Test
    def simpleRender(): Unit = {
        val tpe = typeTag[TestClass].tpe
        println(tpe.decls.map(_.asMethod.typeParams.map(s => s.name + s.typeSignature.toString).mkString("[", ", ", "]")))
    }

    @Test
    def simpleRenderReturnTypes(): Unit = {
        val tpe = typeTag[TestClass].tpe
        println(tpe.decls.map(_.asMethod.returnType.toString))
    }

    @Test
    def renderRecursively(): Unit = {
        val tpe = typeTag[TestClass].tpe
        def typeToString(tpe: Type): String = {
            if (!tpe.takesTypeArgs)
                return tpe.toString
            tpe.toString + tpe.typeArgs.map(typeToString).mkString("[", ", ", "]")
        }
        println(tpe.decls.map(_.asMethod.typeParams.map(s => s.name + typeToString(s.typeSignature)).mkString("[", ", ", "]")))
    }

}

object ScalaReflectionTests {

    class TestClass {

        def genericMethod2[EFFE <: ResourceFolder, S <: ResourceFile, V, G, TS >: V, F <: Option[_ >: List[(EFFE, S)]]]: Option[_ >: List[EFFE]] = None

        def genericMethod[EFFE >: ResourceFolder, I >: ResourceFolder]: Option[_ >: List[EFFE]] = None
    }
}
