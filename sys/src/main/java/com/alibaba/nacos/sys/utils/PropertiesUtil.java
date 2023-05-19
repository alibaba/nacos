/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.sys.utils;

import org.springframework.core.env.Environment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

/**
 * Properties util.
 *
 * @author xiweng.yy
 */
public class PropertiesUtil {
    
    public static Properties getPropertiesWithPrefix(Environment environment, String prefix)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return handleSpringBinder(environment, prefix, Properties.class);
    }
    
    public static Map<String, Object> getPropertiesWithPrefixForMap(Environment environment, String prefix)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return handleSpringBinder(environment, prefix, Map.class);
    }
    
    /**
     * Handle spring binder to bind object.
     *
     * @param environment spring environment
     * @param prefix      properties prefix
     * @param targetClass target class
     * @param <T>         target class
     * @return binder object
     */
    @SuppressWarnings("unchecked")
    public static <T> T handleSpringBinder(Environment environment, String prefix, Class<T> targetClass)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
        Class<?> binderClass = Class.forName("org.springframework.boot.context.properties.bind.Binder");
        Method getMethod = binderClass.getDeclaredMethod("get", Environment.class);
        Method bindMethod = binderClass.getDeclaredMethod("bind", String.class, Class.class);
        Object binderObject = getMethod.invoke(null, environment);
        String prefixParam = prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
        Object bindResultObject = bindMethod.invoke(binderObject, prefixParam, targetClass);
        Method resultGetMethod = bindResultObject.getClass().getDeclaredMethod("get");
        return (T) resultGetMethod.invoke(bindResultObject);
    }
}
