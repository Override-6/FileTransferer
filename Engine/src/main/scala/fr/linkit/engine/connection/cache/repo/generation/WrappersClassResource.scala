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

package fr.linkit.engine.connection.cache.repo.generation

import fr.linkit.api.connection.cache.repo.PuppetWrapper
import fr.linkit.api.connection.cache.repo.generation.GeneratedClassLoader
import fr.linkit.api.local.resource.external.ResourceFolder
import fr.linkit.api.local.resource.representation.{FolderRepresentation, ResourceRepresentationFactory}
import fr.linkit.engine.connection.cache.repo.generation.WrappersClassResource.WrapperPrefixName
import fr.linkit.engine.local.LinkitApplication
import fr.linkit.engine.local.mapping.ClassMappings

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable

//FIXME Critical bug ! The naming system currently can't handle nested / anonymous classes !
class WrappersClassResource(override val resource: ResourceFolder) extends FolderRepresentation {

    private val folderPath           = Path.of(resource.getAdapter.getAbsolutePath)
    private val generatedClasses     = mutable.Map.empty[String, Class[_ <: PuppetWrapper[AnyRef]]]

    def findWrapperClass[S](puppetClass: Class[_]): Option[Class[S with PuppetWrapper[S]]] = {
        val puppetClassName  = puppetClass.getName
        val wrapperClassName = adaptClassName(puppetClassName, WrapperPrefixName)
        generatedClasses.getOrElseUpdate(wrapperClassName, {
            val wrapperClassPath = folderPath.resolve(wrapperClassName.replace('.', File.separatorChar) + ".class")
            if (Files.notExists(wrapperClassPath))
                null
            else {
                var loader = puppetClass.getClassLoader
                if (loader == null)
                    loader = getClass.getClassLoader //Use the Application's classloader

                val classLoader = new GeneratedClassLoader(folderPath, loader, Seq(classOf[LinkitApplication].getClassLoader))
                val clazz = Class.forName(wrapperClassName, false, classLoader).asInstanceOf[Class[_ <: PuppetWrapper[AnyRef]]]
                clazz.getSimpleName //Invoking a method in order to make the class load its reflectionData (causes fatal error if not made directly)
                ClassMappings.putClass(clazz)
                clazz
            }
        }) match {
            case clazz: Class[S with PuppetWrapper[S]] => Some(clazz)
            case _                                     => None
        }
    }

    override protected def initialize(): Unit = ()
}

object WrappersClassResource extends ResourceRepresentationFactory[WrappersClassResource, ResourceFolder] {

    val WrapperPrefixName : String = "Puppet"
    val WrapperMetaPrefixName : String = "Meta"
    val WrapperPackageName: String = "gen"
    val WrapperPackage    : String = WrapperPackageName + "."

    override def apply(resource: ResourceFolder): WrappersClassResource = new WrappersClassResource(resource)
}
