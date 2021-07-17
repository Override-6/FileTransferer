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

package fr.linkit.engine.local.mapping

import fr.linkit.engine.local.utils.NumberSerializer
import org.nustaq.serialization.FSTConfiguration

import java.security.CodeSource
import scala.collection.mutable

object ClassMappings {

    private val primitives = mapPrimitives()
    private val classes    = new mutable.HashMap[Int, (String, ClassLoader)]()
    private val sources    = mutable.HashSet.empty[CodeSource]

    def putClass(className: String, loader: ClassLoader): Unit = {
        //println(s"Class put ! ($className) of hash code ${className.hashCode}")
        classes.put(className.hashCode, (className, loader))
    }

    def addClassPath(source: CodeSource): Unit = sources += source

    def getClassPaths: List[CodeSource] = sources.toList

    def putClass(clazz: Class[_]): Unit = putClass(clazz.getName, clazz.getClassLoader)

    def getClassName(hashCode: Int): String = classes(hashCode)._1

    def findClass(hashCode: Int): Option[Class[_]] = {
        //println(s"Getting class from hashcode $hashCode")
        classes.get(hashCode).map(extractClass).orElse(primitives.get(hashCode))
    }

    def getClass(hashCode: Int): Class[_] = {
        //println(s"Getting class from hashcode $hashCode")
        findClass(hashCode).orNull
    }

    def getClassNameOpt(hashCode: Int): Option[String] = classes.get(hashCode).map(_._1)

    def isRegistered(hashCode: Int): Boolean = classes.contains(hashCode)

    def isRegistered(clazz: Class[_]): Boolean = isRegistered(clazz.getName)

    def isRegistered(className: String): Boolean = classes.contains(className.hashCode)

    private def mapPrimitives(): Map[Int, Class[_]] = {
        import java.{lang => l}
        Array(
            Integer.TYPE,
            l.Byte.TYPE,
            l.Short.TYPE,
            l.Long.TYPE,
            l.Double.TYPE,
            l.Float.TYPE,
            l.Boolean.TYPE,
            Character.TYPE
        ).map(cl => (cl.getName.hashCode, cl))
                .toMap
    }

    private def extractClass(pair: (String, ClassLoader)): Class[_] = {
        val className = pair._1
        primitives.getOrElse(className.hashCode,
            Class.forName(className, false, pair._2))
    }

}
