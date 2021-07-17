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

package fr.linkit.api.connection.cache.repo.description.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contains all the information of a method declared by a class
 * that would be extended at runtime for an object synchronization.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MethodControl {

    /**
     * Specifies what kind of invocation the implementation of the annotated method must do.
     *
     * @see fr.linkit.api.connection.cache.repo.description.annotation.InvocationKind
     */
    InvocationKind value() default InvocationKind.LOCAL_AND_REMOTES;

    /**
     * If true, the returned value, if possible, will be synchronized with the caller as well.
     * as well.
     */
    boolean synchronizeReturnValue() default false;

    /**
     * If a method is hidden, this mean that it can't be called remotely.
     * If a distant object reference call the annotated method with this param on,
     * It will receive a {@link fr.linkit.api.connection.cache.repo.RemoteInvocationFailedException}
     */
    boolean hide() default false;

    boolean invokeOnly() default false;
}
