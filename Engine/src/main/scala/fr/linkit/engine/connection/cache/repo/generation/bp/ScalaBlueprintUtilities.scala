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
import fr.linkit.api.local.generation.PuppetClassDescription

import scala.reflect.runtime.universe._

object ScalaBlueprintUtilities {

    def getGenericParams(desc: PuppetClassDescription[_], transform: Symbol => Any): String = {
        val result = desc
                .classType
                .typeArgs
                .map(t => transform(t.typeSymbol))
                .mkString(",")
        if (result.isEmpty) ""
        else s"[$result]"
    }

    def getReturnType(method: MethodDescription): String = {
        val methodSymbol = method.symbol
        val classType    = method.classDesc.classType
        val classSymbol  = classType.typeSymbol
        val tParams      = classSymbol.typeSignature.typeParams
        val tpe          = methodSymbol.returnType
        renderTypes(Seq({
            val methodOwner = methodSymbol.owner
            tpe.asSeenFrom(classType, methodOwner).finalResultType
        }), tParams).mkString("")
    }

    def getGenericParamsIn(method: MethodDescription): String = {
        val symbol      = method.symbol
        val classType   = method.classDesc.classType
        val cTypeParams = classType.typeParams
        val mTypeParams = symbol.typeParams
        if (mTypeParams.isEmpty)
            return ""
        mTypeParams
                .map(_.typeSignature.asSeenFrom(classType, symbol.owner).finalResultType)
                .zipWithIndex
                .map(pair => {
                    val i   = pair._2
                    val tpe = mTypeParams(i)
                    if (cTypeParams.exists(_.name == tpe.name)) s"$$_$i"
                    else tpe.name.toString + pair._1
                })
                .mkString("[", ",", "]")
    }

    def getGenericParamsOut(method: MethodDescription): String = {
        val cTypeParams = method
                .classDesc
                .classType
                .typeParams
        val mTypeParams = method
                .symbol
                .typeSignature
                .typeParams
                .zipWithIndex
        if (mTypeParams.isEmpty)
            return ""
        mTypeParams
                .zipWithIndex
                .map(pair => {
                    val i    = pair._2
                    val name = mTypeParams(i)._1.name.toString
                    if (cTypeParams.exists(_.name.toString == name)) s"$$_$i"
                    else name
                })
                .mkString("[", ",", "]")
    }

    def getParameters(method: MethodDescription)(firstMkString: Seq[String] => String,
                                                 secondMkString: Seq[String] => String,
                                                 allowTypes: Boolean, allowVarargUpcast: Boolean): String = {
        var i = 0

        def n = {
            i += 1
            i
        }

        val symbol = method.symbol

        def argType(s: Symbol): String = {
            val classType = method.classDesc.classType
            val str       = renderTypes({
                Seq(s
                        .typeSignature
                        .asSeenFrom(classType, symbol.owner).finalResultType)
            }, classType.typeParams).head
            if (str.endsWith("*") && allowVarargUpcast) s": _*"
            else if (allowTypes) ": " + str else ""
        }

        secondMkString {
            symbol
                    .paramLists
                    .map(l => {
                        var markedAsImplicit = false
                        firstMkString(l.map(s => {
                            val result = s"arg${n}${argType(s)}"
                            if (allowTypes && !markedAsImplicit && s.isImplicit) {
                                markedAsImplicit = true
                                "implicit " + result
                            } else result
                        }))
                    })
        }
    }

    def renderTypes(types: Seq[Type], classLevelTypes: Seq[Symbol]): Seq[String] = {
        if (types.isEmpty)
            return Seq.empty

        def renderType(t: Type, slot: Symbol, typePos: Int): String = {
            val tpe      = t.finalResultType
            val args     = tpe.typeArgs
            val symbol   = tpe.typeSymbol
            val fullName = symbol.fullName
            if (slot != null && slot.typeSignature.takesTypeArgs) {
                val ownerType       = slot.typeSignature
                val typeDeclaration = ownerType.typeParams(typePos)
                if (typeDeclaration.typeSignature.takesTypeArgs)
                    return if (symbol.isParameter) symbol.name.toString else fullName
            }
            lazy val value = {
                if (args.isEmpty) ""
                else args
                        .zipWithIndex
                        .map(pair => renderType(pair._1, symbol, pair._2))
                        .mkString("[", ",", "]")
            }
            if (fullName.startsWith("scala.<"))
                return tpe.toString
            val idx = classLevelTypes.indexWhere(_.name.toString == symbol.name.toString)
            if (idx != -1 && classLevelTypes.forall(_.fullName != fullName))
                return s"$$_$idx" + (if (args.nonEmpty) ' ' + value else "")
            if (tpe.toString.endsWith("[_]") || fullName.endsWith("<refinement>"))
                return tpe.toString
            val result = (if (symbol.isParameter) symbol.name.toString else fullName) + value
            result
        }

        for (tpe <- types) yield renderType(tpe, null, 0)
    }

}
