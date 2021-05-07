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

package fr.linkit.engine.connection.network.cache.repo.generation

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{Files, Path, StandardOpenOption}

import fr.linkit.api.connection.network.cache.repo.PuppetWrapper
import fr.linkit.api.local.resource.external.ResourceFolder
import fr.linkit.api.local.resource.representation.{FolderRepresentation, ResourceRepresentationFactory}
import fr.linkit.engine.connection.network.cache.repo.generation.WrappersClassResource.{ClassesFolder, SourcesFolder}
import javax.tools.ToolProvider

import scala.collection.mutable

class WrappersClassResource(override val resource: ResourceFolder) extends FolderRepresentation {

    private val folderPath           = Path.of(resource.getAdapter.getAbsolutePath)
    private val queuePath            = Path.of(folderPath + SourcesFolder)
    private val generatedClassesPath = Path.of(folderPath + ClassesFolder)
    private val classLoader          = new URLClassLoader(Array(generatedClassesPath.toUri.toURL))
    private val generatedClasses     = mutable.Map.empty[String, Class[_ <: PuppetWrapper[Serializable]]]

    def addToQueue(className: String, classSource: String): Unit = {
        val path = queuePath.resolve(className + ".java")
        Files.writeString(path, classSource, StandardOpenOption.CREATE)
    }

    def getWrapperClass[S <: Serializable](puppetClassName: String): Option[Class[S with PuppetWrapper[S]]] = {
        generatedClasses.getOrElseUpdate(puppetClassName, {
            val wrapperClassPath = generatedClassesPath.resolve(puppetClassName.replace('.', File.separatorChar))
            if (Files.notExists(wrapperClassPath))
                null
            else {
                classLoader.loadClass(puppetClassName).asInstanceOf[Class[_ <: PuppetWrapper[Serializable]]]
            }
        }) match {
            case clazz: Class[S with PuppetWrapper[S]] => Some(clazz)
            case _                                     => None
        }
    }

    def compileQueue(): Unit = {
        val javac = ToolProvider.getSystemJavaCompiler

        javac.run(null, null, null, "--help")
        val code = javac.run(null, null, null, "javac", "-d", "\"" + generatedClassesPath.toString + "\\\"", "-Xlint:all", queuePath.toString)
        if (code != 0)
            throw new InvalidPuppetDefException(s"Javac rejected class queue compilation. See above messages for further details. (error code: $code)")

        clearQueue()
    }

    def removeFromQueue(className: String): Unit = {
        val path = queuePath.resolve(className)
        Files.deleteIfExists(path)
    }

    initialize()

    override def initialize(): Unit = {
        Files.createDirectories(queuePath)
        Files.createDirectories(generatedClassesPath)
        clearQueue()
    }

    override def close(): Unit = {
        clearQueue()
    }

    private def clearQueue(): Unit = {
        Files.list(queuePath)
            .forEach(element => Files.deleteIfExists(element))
    }

}

object WrappersClassResource extends ResourceRepresentationFactory[WrappersClassResource, ResourceFolder] {

    val SourcesFolder: String = "/Sources/"
    val ClassesFolder: String = "/Classes/"

    override def apply(resource: ResourceFolder): WrappersClassResource = new WrappersClassResource(resource)
}