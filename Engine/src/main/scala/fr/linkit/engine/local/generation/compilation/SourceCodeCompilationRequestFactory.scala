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

package fr.linkit.engine.local.generation.compilation

import fr.linkit.api.connection.cache.repo.generation.GeneratedClassLoader
import fr.linkit.api.local.generation.compilation.CompilationResult
import fr.linkit.engine.local.LinkitApplication
import fr.linkit.engine.local.generation.compilation.SourceCodeCompilationRequest.SourceCode

import java.nio.file.Path

object SourceCodeCompilationRequestFactory extends AbstractCompilationRequestFactory[(SourceCode, ClassLoader), Class[_]] {

    //TODO the code can be factorised (compare with AbstractCompilationRequestFactory, SourceCodeCompilationRequestFactory and WrapperCompilationRequestFactory)
    override protected def createMultiRequest(contexts: Seq[(SourceCode, ClassLoader)], workingDir: Path): SourceCodeCompilationRequest[Seq[Class[_]]] = {
        new SourceCodeCompilationRequest[Seq[Class[_]]] { request =>
            override val workingDirectory: Path            = workingDir
            override var sourceCodes     : Seq[SourceCode] = contexts.map(_._1)

            override def conclude(outs: Seq[Path], compilationTime: Long): CompilationResult[Seq[Class[_]]] = {
                new AbstractCompilationResult[Seq[Class[_]]](outs, compilationTime, request) {
                    override def getResult: Option[Seq[Class[_]]] = {
                        Some(contexts
                                .filter(context => outs.contains(workingDir.resolve(context._1.className)))
                                .map { context =>
                                    val loader = new GeneratedClassLoader(workingDir, context._2, Seq(classOf[LinkitApplication].getClassLoader))
                                    val clazz = Class.forName(context._1.className, true, loader)
                                    clazz.getDeclaredFields //Invoking a method in order to make the class load its reflectionData (causes fatal error if not made directly)
                                    clazz
                                })
                    }
                }
            }
        }
    }
}
