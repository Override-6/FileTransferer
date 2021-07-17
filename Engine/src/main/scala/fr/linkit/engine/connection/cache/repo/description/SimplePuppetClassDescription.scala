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

package fr.linkit.engine.connection.cache.repo.description

import fr.linkit.api.connection.cache.repo.PuppetWrapper
import fr.linkit.api.connection.cache.repo.description.{FieldDescription, MethodDescription, fullNameOf}
import fr.linkit.api.local.generation.PuppetClassDescription

import java.lang.reflect.Method
import scala.reflect.runtime.{universe => u}

class SimplePuppetClassDescription[A] private(override val classType: u.Type,
                                           override val clazz: Class[A], val loader: ClassLoader) extends PuppetClassDescription[A] {

    import u._

    /**
     * Methods and fields that comes from those classes will not be available for RMI Invocations.
     * */
    private val BlacklistedSuperClasses: Array[String] = Array(fullNameOf[Any], fullNameOf[Object], fullNameOf[Product])

    private val mirror = u.runtimeMirror(loader)

    private val methods = collectMethods()
    private val fields  = collectFields()

    override def listMethods(): Iterable[MethodDescription] = {
        methods.values
    }

    override def getMethodDescription(methodID: Int): Option[MethodDescription] = {
        methods.get(methodID)
    }

    override def listFields(): Seq[FieldDescription] = {
        fields.values.toSeq
    }

    override def getFieldDescription(fieldID: Int): Option[FieldDescription] = {
        fields.get(fieldID)
    }

    private def collectMethods(): Map[Int, MethodDescription] = {
        def getFiltered(tpe: Type): Iterable[MethodSymbol] = {
            tpe.members
                    .filter(_.isMethod)
                    .map(_.asMethod)
                    .filter(_.owner.isClass)
                    .filterNot(f => f.isFinal || f.isStatic || f.isConstructor || f.isPrivate || f.isPrivateThis || f.privateWithin != NoSymbol)
                    .filterNot(f => f.owner.fullName.startsWith("scala.Function") || f.owner.fullName.startsWith("scala.PartialFunction"))
            //.filterNot(f => f.owner == symbolOf[Any])
        }

        getFiltered(classType)
                .filterNot(m => BlacklistedSuperClasses.contains(m.owner.fullName))
                .concat(if (classType.typeSymbol.isJava) Seq() else getFiltered(typeOf[Any])
                        .filter(_.name.toString != "getClass")) //don't know why, but the "getClass" method if scala.Any is not defined as final
                .filter(_.name.toString != "equals") //FIXME scalac error : "name clash between defined and inherited member"
                .map(genMethodDescription)
                .map(desc => (desc.methodId, desc))
                .toMap
    }

    private def genMethodDescription(symbol: u.MethodSymbol): MethodDescription = {
        val javaMethod = asJavaMethod(symbol)
        MethodDescription(symbol, javaMethod, this)
    }

    def asJavaMethod(method: u.MethodSymbol): Method = {
        val symbolParams = method.paramLists
                .flatten
                .map(t => {
                    val argClass = t.typeSignature.erasure.typeSymbol.asClass
                    if (argClass.fullName == "scala.Array")
                        "array"
                    else mirror
                            .runtimeClass(argClass)
                            .descriptorString()
                }
                )
        clazz
                .getMethods
                .filter(_.getName == method.name.encodedName.toString)
                .find(x => {
                    val v = x.getParameterTypes
                            .map(t => if (t.isArray) "array" else t.descriptorString())
                    v sameElements symbolParams
                })
                .get
    }

    private def collectFields(): Map[Int, FieldDescription] = {
        classType.decls
                .filter(_.isMethod)
                .map(_.asMethod)
                .filter(_.isGetter)
                .filterNot(f => BlacklistedSuperClasses.contains(f.owner.fullName))
                .map(FieldDescription(_, this))
                .map(desc => (desc.fieldId, desc))
                .toMap
    }

}

object SimplePuppetClassDescription {

    import u._

    val JavaEqualsMethodID         = 21

    def apply[A](clazz: Class[A]): SimplePuppetClassDescription[A] = {
        if (classOf[PuppetWrapper[_]].isAssignableFrom(clazz))
            throw new IllegalArgumentException("Provided class already extends from PuppetWrapper")

        val tpe = runtimeMirror(clazz.getClassLoader).classSymbol(clazz).selfType
        new SimplePuppetClassDescription(tpe, clazz, clazz.getClassLoader)
    }

}
