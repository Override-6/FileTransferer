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

package fr.linkit.engine.test.classes;

public class JavaClass<AS> {

    static class A extends JavaClass<String> {
        public JavaClass<String> test(JavaClass<String> test) {
            return this;
        }
    }

    static class B extends A {
        @Override
        public B test(JavaClass<String> test) {
            return this;
        }
    }
}
