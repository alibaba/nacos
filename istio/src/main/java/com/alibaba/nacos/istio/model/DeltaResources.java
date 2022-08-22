/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.istio.model;

import com.alibaba.nacos.istio.misc.Loggers;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**.
 * @author RocketEngine26
 * @date 2022/8/19 下午4:21
 */
public class DeltaResources {
    private final Map<String, PushChange.ChangeType> serviceChangeMap = new ConcurrentHashMap<>();
    
    private final Map<String, PushChange.ChangeType> instanceChangeMap = new ConcurrentHashMap<>();
    
    private final Set<String> removedHostName = new HashSet<>();
    
    private final Set<String> removedClusterName = new HashSet<>();
    
    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    public void putChangeType(String cate, String name, PushChange.ChangeType type) {
        if ("service".equals(cate)) {
            Loggers.MAIN.info("service equal:" + name);
            serviceChangeMap.put(name, type);
        } else if ("instance".equals(cate)) {
            Loggers.MAIN.info("instance equal:" + name);
            instanceChangeMap.put(name, type);
        }
    }
    
    public void addRemovedHostName(String hostName) {
        removedHostName.add(hostName);
    }
    
    public void addRemovedClusterName(String clusterName) {
        removedClusterName.add(clusterName);
    }
    
    public Map<String, PushChange.ChangeType> getServiceChangeMap() {
        return serviceChangeMap;
    }
    
    public Map<String, PushChange.ChangeType> getInstanceChangeMap() {
        return instanceChangeMap;
    }
    
    public Set<String> getRemovedHostName() {
        return removedHostName;
    }
    
    public Set<String> getRemovedClusterName() {
        return removedClusterName;
    }
}
