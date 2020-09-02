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
import com.google.common.collect.Lists;
import org.reflections.Reflections;

import java.lang.reflect.Modifier;
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
    
    static boolean inited = false;
    
    public static void init() {
        scan();
    }
    
    private static synchronized void scan() {
        if (inited) {
            return;
        }
        List<String> scanPackage = Lists.newArrayList("com.alibaba.nacos.api.naming.remote.request",
                "com.alibaba.nacos.api.naming.remote.response", "com.alibaba.nacos.api.config.remote.request",
                "com.alibaba.nacos.api.config.remote.response", "com.alibaba.nacos.api.remote.request",
                "com.alibaba.nacos.api.remote.response", "com.alibaba.nacos.naming.cluster.remote.request",
                "com.alibaba.nacos.naming.cluster.remote.response");
        for (String pkg : scanPackage) {
            Reflections reflections = new Reflections(pkg);
            Set<Class<? extends Request>> subTypesRequest = reflections.getSubTypesOf(Request.class);
            Set<Class<? extends Response>> subTypesOfResponse = reflections.getSubTypesOf(Response.class);
            for (Class clazz : subTypesRequest) {
                register(clazz.getName(), clazz);
            }
            for (Class clazz : subTypesOfResponse) {
                register(clazz.getName(), clazz);
            }
        }
        inited = true;
        
    }
    
    static void register(String type, Class clazz) {
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }
        if (Modifier.isInterface(clazz.getModifiers())) {
            return;
        }
        if (REGISTRY_REQUEST.containsKey(type)) {
            throw new RuntimeException(String.format("Fail to register, type:%s ,clazz:%s ", type, clazz.getName()));
        }
        REGISTRY_REQUEST.put(type, clazz);
    }
    
    public static Class getClassbyType(String type) {
        return REGISTRY_REQUEST.get(type);
    }
}
