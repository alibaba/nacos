/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import org.reflections.Reflections;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * payload regitry,include all request and response.
 *
 * @author liuzunfei
 * @version $Id: PayloadRegistry.java, v 0.1 2020年09月01日 10:56 AM liuzunfei Exp $
 */

public class PayloadRegistry {
    
    private static final Map<String, Class> REGISTRY_REQUEST = new HashMap<String, Class>();
    
    static boolean initialized = false;
    
    public static void init() {
        scan();
    }
    
    private static synchronized void scan() {
        if (initialized) {
            return;
        }
        
        List<String> requestScanPackage = Arrays
                .asList("com.alibaba.nacos.api.naming.remote.request", "com.alibaba.nacos.api.config.remote.request",
                        "com.alibaba.nacos.api.remote.request", "com.alibaba.nacos.naming.cluster.remote.request");
        for (String pkg : requestScanPackage) {
            Reflections reflections = new Reflections(pkg);
            // 获取指定包下所有Request.class接口的子类(可能包括抽象类和接口)
            Set<Class<? extends Request>> subTypesRequest = reflections.getSubTypesOf(Request.class);
            for (Class clazz : subTypesRequest) {
                // key=类名,value=class对象,
                // 缓存当前项目指定的包名requestScanPackage下所有的Request.class接口的子类信息到REGISTRY_REQUEST中
                register(clazz.getSimpleName(), clazz);
            }
        }
        
        List<String> responseScanPackage = Arrays
                .asList("com.alibaba.nacos.api.naming.remote.response",
                "com.alibaba.nacos.api.config.remote.response", "com.alibaba.nacos.api.remote.response",
                "com.alibaba.nacos.naming.cluster.remote.response");
        for (String pkg : responseScanPackage) {
            Reflections reflections = new Reflections(pkg);
            // 获取指定包下所有Response.class接口的子类(可能包括抽象类和接口)
            Set<Class<? extends Response>> subTypesOfResponse = reflections.getSubTypesOf(Response.class);
            for (Class clazz : subTypesOfResponse) {
                // key=类名,value=class对象,
                // 缓存当前项目指定的包名responseScanPackage下所有的Response.class接口的子类信息到REGISTRY_REQUEST中
                register(clazz.getSimpleName(), clazz);
            }
        }
        // 注意,Request.class接口的子类信息和Response.class接口的子类信息都缓存到了REGISTRY_REQUEST缓存中(似乎这个命名有问题)
        initialized = true;
    }
    
    static void register(String type, Class clazz) {
        // 排除抽象类
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }
        // 排除接口
        if (Modifier.isInterface(clazz.getModifiers())) {
            return;
        }
        if (REGISTRY_REQUEST.containsKey(type)) {
            throw new RuntimeException(String.format("Fail to register, type:%s ,clazz:%s ", type, clazz.getName()));
        }
        REGISTRY_REQUEST.put(type, clazz);
    }
    
    public static Class getClassByType(String type) {
        return REGISTRY_REQUEST.get(type);
    }
}
