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

package fr.linkit.api.connection.cache.repo

/**
 * The Chip is a class that controls an object of type [[S]]
 * The controlled object is then considered as chipped and its chip
 * can invoke any of its methods and set his fields values.
 * */
trait Chip[S] {

    /**
     * this method will transfer to the chipped object every fields
     * values of the given object.
     * @param obj the obj in where fields values will be pasted to the chipped object.
     * */
    def updateObject(obj: S): Unit

    /**
     * Invokes the method of the chipped object.
     * The method is determined by the methoidID integer. (see [[SimplePuppetClassDescription]]
     * */
    def callMethod(methodID: Int, params: Array[Array[Any]]): Any
}
