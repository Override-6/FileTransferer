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

package fr.linkit.api.connection.cache.repo;

import fr.linkit.api.connection.cache.repo.description.PuppeteerInfo;
import fr.linkit.api.connection.cache.repo.description.WrapperBehavior;

import java.io.Serializable;

public interface PuppetWrapper<T> extends Serializable {

    void initPuppeteer(Puppeteer<T> puppeteer);

    Puppeteer<T> getPuppeteer();

    WrapperBehavior<T> getBehavior();

    PuppeteerInfo getPuppeteerDescription();

    InvocationChoreographer getChoreographer();

    boolean isInitialized();

    T asWrapped();

    T detachedSnapshot();

    Class<T> getWrappedClass();

}
