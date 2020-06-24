/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.common.utils;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;

import static com.alibaba.nacos.api.exception.NacosException.SERVER_ERROR;

/**
 * Utils for Class.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ClassUtils {
    
    /**
     * Finds and returns class by className.
     *
     * @param className String value for className.
     * @return class Instances of the class represent classes and interfaces.
     */
    public static Class findClassByName(String className) {
        try {
            return Class.forName(className);
        } catch (Exception e) {
            throw new NacosRuntimeException(SERVER_ERROR, "this class name not found");
        }
    }
    
    /**
     * Determines if the class or interface represented by this object is either the same as, or is a superclass or
     * superinterface of, the class or interface represented by the specified parameter.
     *
     * @param clazz Instances of the class represent classes and interfaces.
     * @param cls   Instances of the class represent classes and interfaces.
     * @return the value indicating whether objects of the type can be assigned to objects of this class.
     */
    public static boolean isAssignableFrom(Class clazz, Class cls) {
        Objects.requireNonNull(cls, "cls");
        return clazz.isAssignableFrom(cls);
    }
    
    /**
     * Gets and returns the class name.
     *
     * @param cls Instances of the class represent classes and interfaces.
     * @return the name of the class or interface represented by this object.
     */
    public static String getName(Class cls) {
        Objects.requireNonNull(cls, "cls");
        return cls.getName();
    }
    
    /**
     * Gets and returns className.
     *
     * @param obj Object instance.
     * @return className.
     */
    public static String getName(Object obj) {
        Objects.requireNonNull(obj, "obj");
        return obj.getClass().getName();
    }
    
    /**
     * Gets and returns the canonical name of the underlying class.
     *
     * @param cls Instances of the class represent classes and interfaces.
     * @return The canonical name of the underlying class.
     */
    public static String getCanonicalName(Class cls) {
        Objects.requireNonNull(cls, "cls");
        return cls.getCanonicalName();
    }
    
    /**
     * Gets and returns the canonical name of the underlying class.
     *
     * @param obj Object instance.
     * @return The canonical name of the underlying class.
     */
    public static String getCanonicalName(Object obj) {
        Objects.requireNonNull(obj, "obj");
        return obj.getClass().getCanonicalName();
    }
    
    /**
     * Gets and returns the simple name of the underlying class.
     *
     * @param cls Instances of the class represent classes and interfaces.
     * @return the simple name of the underlying class.
     */
    public static String getSimplaName(Class cls) {
        Objects.requireNonNull(cls, "cls");
        return cls.getSimpleName();
    }
    
    /**
     * Gets and returns the simple name of the underlying class as given in the source code.
     *
     * @param obj Object instance.
     * @return the simple name of the underlying class.
     */
    public static String getSimplaName(Object obj) {
        Objects.requireNonNull(obj, "obj");
        return obj.getClass().getSimpleName();
    }
    
}
