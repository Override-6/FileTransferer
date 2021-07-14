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

public enum InvocationKind {

    /**
     * The invocation will only be performed on the current object.
     * This behavior is the same as calling any normal java method.
     */
    ONLY_LOCAL(true),
    /**
     * Invocation will only be invoked on the machine that owns the original object.
     */
    ONLY_OWNER(false),
    /**
     * The invocation will only be performed on every remote machines.
     * The return value of the invocation will come from the machine that owns the original object.
     */
    ONLY_REMOTES(false),
    /**
     * The invocation will be performed on the current machine <b>and</b> on every remote machines.
     * The return value of the invocation will come from the current machine.
     * <br>
     * <u>This is the normal behavior.</u>
     */
    LOCAL_AND_REMOTES(true),
    /**
     * The invocation will be performed on the current machine <b>and</b> on the machine that owns the original object.
     * The return value of the invocation will come from the local machine.
     */
    LOCAL_AND_OWNER(true);

    private final boolean forceLocalInvocation;

    InvocationKind(boolean forceLocalInvocation) {
        this.forceLocalInvocation = forceLocalInvocation;
    }

    public boolean isLocalInvocationForced() {
        return forceLocalInvocation;
    }
}
