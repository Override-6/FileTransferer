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

import fr.linkit.api.connection.cache.repo.description.annotation.InvocationKind
import fr.linkit.api.connection.cache.repo.description.{MemberBehaviorFactory, MethodBehavior, TreeViewBehavior, WrapperBehavior}
import fr.linkit.engine.connection.cache.repo.description.WrapperBehaviorBuilder.MethodControl

import java.util
import scala.reflect.ClassTag

class WrapperBehaviorBuilder[T] private(desc: WrapperBehavior[T]) {

    def this(clazz: Class[T], memberBehaviorFactory: MemberBehaviorFactory) {
        this(SimpleWrapperBehavior(SimplePuppetClassDescription(clazz), new TreeViewDefaultBehavior(memberBehaviorFactory)))
    }

    def this(memberBehaviorFactory: MemberBehaviorFactory)(implicit classTag: ClassTag[T]) = {
        this(classTag.runtimeClass.asInstanceOf[Class[T]], memberBehaviorFactory)
    }

    def this(treeView: TreeViewBehavior)(implicit classTag: ClassTag[T]) {
        this(SimpleWrapperBehavior(SimplePuppetClassDescription(classTag.runtimeClass.asInstanceOf[Class[T]]), treeView))
    }

    final def annotate(name: String, params: Class[_]*): MethodModification = {
        val methodID = name.hashCode + util.Arrays.hashCode(params.toArray[AnyRef])
        val method   = desc.getMethodBehavior(methodID).getOrElse(throw new NoSuchElementException(s"Method description '$name' not found."))
        new MethodModification(Seq(method))
    }

    final def annotateAll(name: String): MethodModification = {
        new MethodModification(
            desc.listMethods()
                    .filter(_.desc.symbol.name.toString == name)
        )
    }

    final def annotateAll: MethodModification = {
        new MethodModification(desc.listMethods())
    }

    def build: WrapperBehavior[T] = desc

    class MethodModification private[WrapperBehaviorBuilder](descs: Iterable[MethodBehavior]) {

        def by(control: MethodControl): Unit = descs.foreach { bhv =>
            bhv.invocationKind = control.value
            bhv.synchronizedParams = if (control.synchronizedParams != null) control.synchronizedParams else bhv.synchronizedParams
            bhv.syncReturnValue = control.synchronizeReturnValue
            bhv.isHidden = control.hide
        }

        def and(otherName: String): MethodModification = {
            new MethodModification(descs ++ desc.listMethods()
                    .filter(_.desc.symbol.name.toString == otherName))
        }
    }

}

object WrapperBehaviorBuilder {


    case class MethodControl(value: InvocationKind, synchronizeReturnValue: Boolean = false, hide: Boolean = false, synchronizedParams: Seq[Boolean] = null)

}
