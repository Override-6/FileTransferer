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
 * Specifies that a remote invocation should not await result or exception from the distant method.
 * if true, the underlying class will only send the invocation request to the distant object's owner and
 * return what's specified by {@link InvokeOnly#value()}. arguments declared as mutable with {@link MethodControl}
 * will thus not be modified.
 * */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvokeOnly {
    /**
     * value represents the value to return in a generated method annotated with @InvokeOnly.
     * The value can be something else, such as 'this' in case of method chaining.
     *
     * Note that this value is compiled by javac, and the scope of the compiled string is the underlying method.
     * the trailing ';' character is not necessarily required.
     * */
    String value() default "localResult";

}
