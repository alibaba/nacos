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

package com.alibaba.nacos.common.packagescan;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Scan all appropriate Class object through the package name.
 *
 * @author hujun
 */
public interface PackageScan {
    
    /**
     * Scan all appropriate Class object through the package name and Class object.
     *
     * @param pkg          package name,for example, com.alibaba.nacos.common
     * @param requestClass super class
     * @param <T>          Class type
     * @return a set contains Class
     */
    <T> Set<Class<T>> getSubTypesOf(String pkg, Class<T> requestClass);
    
    /**
     * Scan all appropriate Class object through the package name and annotation.
     *
     * @param pkg        package name,for example, com.alibaba.nacos.common
     * @param annotation annotation
     * @param <T>        Class type
     * @return a set contains Class object
     */
    <T> Set<Class<T>> getTypesAnnotatedWith(String pkg, Class<? extends Annotation> annotation);
    
}
