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
package com.alibaba.nacos.common.util;

/**
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.9.0
 */
public class ClassUtils {

    public static ClassLoader getDefaultClassLoader() {
        try {
            return Thread.currentThread().getContextClassLoader();
        } catch (Throwable t) {
            // ignore
        }

        ClassLoader classLoader = ClassUtils.class.getClassLoader();

        if (classLoader != null) {
            return classLoader;
        }

        try {
            return ClassLoader.getSystemClassLoader();
        } catch (Throwable t) {
            // ignore
        }

        return null;
    }

    public static boolean isPresent(String className) {
        ClassLoader defaultClassLoader = getDefaultClassLoader();

        try {
            if (defaultClassLoader != null) {
                defaultClassLoader.loadClass(className);
            } else {
                Class.forName(className);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
