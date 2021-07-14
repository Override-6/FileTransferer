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

package fr.linkit.engine.connection.cache.repo.generation.bp

import fr.linkit.api.connection.cache.repo.description.MethodDescription
import fr.linkit.api.local.generation.compilation.access.CompilerType
import fr.linkit.engine.connection.cache.repo.generation.bp.ScalaBlueprintUtilities._
import fr.linkit.engine.connection.cache.repo.generation.bp.ScalaWrapperMethodBlueprint.ValueScope
import fr.linkit.engine.local.generation.cbp.{AbstractClassBlueprint, AbstractValueScope}
import fr.linkit.engine.local.generation.compilation.access.CommonCompilerTypes

import java.io.InputStream
import scala.reflect.runtime.universe.symbolOf

class ScalaWrapperMethodBlueprint(bp: InputStream) extends AbstractClassBlueprint[MethodDescription](bp) {

    override val compilerType: CompilerType   = CommonCompilerTypes.Scalac
    override val rootScope   : RootValueScope = RootValueScope(new ValueScope("ROOT", "", 0))

}

object ScalaWrapperMethodBlueprint {

    class ValueScope(name: String, blueprint: String, pos: Int) extends AbstractValueScope[MethodDescription](name, pos, blueprint) {

        bindValue("ReturnType" ~> getReturnType)
        bindValue("DefaultReturnValue" ~> (_.getDefaultTypeReturnValue))
        bindValue("GenericTypesIn" ~> getGenericParamsIn)
        bindValue("GenericTypesOut" ~> getGenericParamsOut)
        bindValue("MethodName" ~> (_.symbol.name.toString))
        bindValue("MethodID" ~> (_.methodId.toString))
        bindValue("ParamsIn" ~> (getParameters(_)(_.mkString("(", ", ", ")"), _.mkString(""), true, false)))
        bindValue("ParamsOut" ~> (getParameters(_)(_.mkString("(", ", ", ")"), _.mkString(""), false, true)))
        bindValue("ParamsOutArray" ~> (getParameters(_)(_.mkString(", "), _.mkString("Array[Any](", ", ", ")"), false, false)))
        bindValue("Override" ~> chooseOverride)

    }

    private def chooseOverride(desc: MethodDescription): String = {
        val symbol = desc.symbol
        val owner  = symbol
                .overrides
                .lastOption
                .map(_.owner)
                .getOrElse(symbol.owner)
        if (owner == symbolOf[Any] || owner == symbolOf[Object])
            "override"
        else ""
    }

}

