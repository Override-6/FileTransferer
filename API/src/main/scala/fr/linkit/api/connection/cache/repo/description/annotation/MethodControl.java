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

import org.jetbrains.annotations.Contract;

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
     * @see fr.linkit.api.connection.cache.repo.description.annotation.InvocationKind
     * */
    InvocationKind value() default InvocationKind.LOCAL_AND_REMOTES;

    /**
     * Specifies that the annotated method has no visible side effects.
     * A pure method is a method that does not mutate the receiver object and/or does not mutates its arguments.
     * If a method is declared as pure, but declares mutable arguments too, the returned boolean of this method should
     * be ignored, and used as false.
     *
     * @return true if the annotated method is a pure method.
     * @see Contract#pure()
     */
    boolean pure() default false;

    /**
     * Informs what arguments of the annotated method have a chance to be mutated.
     * <p>
     * the following values are possible:
     * <table summary="">
     *   <tr><td>"this"</td><td>Method mutates the receiver object, and doesn't mutates any objects passed as arguments (cannot be applied for static method or constructor)</td></tr>
     *   <tr><td>"arg1", "arg2", ...</td><td>Method mutates the N-th argument</td></tr>
     *   <tr><td>"this,arg1"</td><td>Method mutates the receiver and first argument and doesn't mutate any other arguments</td></tr>
     * </table>
     */
    String mutates() default "";

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
}
