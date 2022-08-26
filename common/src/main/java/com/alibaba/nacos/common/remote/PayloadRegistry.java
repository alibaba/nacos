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

package com.alibaba.nacos.common.remote;

import com.alibaba.nacos.api.remote.Payload;
import com.alibaba.nacos.common.packagescan.DefaultPackageScan;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * payload regitry,Define basic scan behavior request and response.
 *
 * @author liuzunfei
 * @author hujun
 * @version $Id: PayloadRegistry.java, v 0.1 2020年09月01日 10:56 AM liuzunfei Exp $
 */

public class PayloadRegistry {
    
    private static final Map<String, Class<?>> REGISTRY_REQUEST = new HashMap<>();
    
    static boolean initialized = false;
    
    public static void init() {
        scan();
    }
    
    private static synchronized void scan() {
        if (initialized) {
            return;
        }
        
        ServiceLoader<PayloadPackageProvider> payloadPackages = ServiceLoader.load(PayloadPackageProvider.class);
        Set<String> result = new HashSet<>();
        for (PayloadPackageProvider payloadPackage : payloadPackages) {
            result.addAll(payloadPackage.getScanPackage());
        }
        for (String pkg : result) {
            DefaultPackageScan packageScan = new DefaultPackageScan();
            Set<Class<Payload>> subTypesResponse = packageScan.getSubTypesOf(pkg, Payload.class);
            for (Class<?> clazz : subTypesResponse) {
                register(clazz.getSimpleName(), clazz);
            }
        }
        initialized = true;
    }
    
    static void register(String type, Class<?> clazz) {
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }
        if (REGISTRY_REQUEST.containsKey(type)) {
            throw new RuntimeException(String.format("Fail to register, type:%s ,clazz:%s ", type, clazz.getName()));
        }
        REGISTRY_REQUEST.put(type, clazz);
    }
    
    public static Class<?> getClassByType(String type) {
        return REGISTRY_REQUEST.get(type);
    }
}
