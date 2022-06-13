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

package com.alibaba.nacos.naming.core.v2;

import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos service manager for v2.
 *
 * @author xiweng.yy
 */
public class ServiceManager {
    
    private static final ServiceManager INSTANCE = new ServiceManager();
    
    private final ConcurrentHashMap<Service, Service> singletonRepository;
    
    private final ConcurrentHashMap<String, Set<Service>> namespaceSingletonMaps;
    
    private ServiceManager() {
        singletonRepository = new ConcurrentHashMap<>(1 << 10);
        namespaceSingletonMaps = new ConcurrentHashMap<>(1 << 2);
    }
    
    public static ServiceManager getInstance() {
        return INSTANCE;
    }
    
    public Set<Service> getSingletons(String namespace) {
        return namespaceSingletonMaps.getOrDefault(namespace, new HashSet<>(1));
    }
    
    /**
     * Get singleton service. Put to manager if no singleton.
     *
     * @param service new service
     * @return if service is exist, return exist service, otherwise return new service
     */
    public Service getSingleton(Service service) {
        singletonRepository.putIfAbsent(service, service);
        Service result = singletonRepository.get(service);
        namespaceSingletonMaps.computeIfAbsent(result.getNamespace(), (namespace) -> new ConcurrentHashSet<>());
        namespaceSingletonMaps.get(result.getNamespace()).add(result);
        return result;
    }
    
    /**
     * Get singleton service if Exist.
     *
     * @param namespace namespace of service
     * @param group     group of service
     * @param name      name of service
     * @return singleton service if exist, otherwise null optional
     */
    public Optional<Service> getSingletonIfExist(String namespace, String group, String name) {
        return getSingletonIfExist(Service.newService(namespace, group, name));
    }
    
    /**
     * Get singleton service if Exist.
     *
     * @param service service template
     * @return singleton service if exist, otherwise null optional
     */
    public Optional<Service> getSingletonIfExist(Service service) {
        return Optional.ofNullable(singletonRepository.get(service));
    }
    
    public Set<String> getAllNamespaces() {
        return namespaceSingletonMaps.keySet();
    }
    
    /**
     * Remove singleton service.
     *
     * @param service service need to remove
     * @return removed service
     */
    public Service removeSingleton(Service service) {
        if (namespaceSingletonMaps.containsKey(service.getNamespace())) {
            namespaceSingletonMaps.get(service.getNamespace()).remove(service);
        }
        return singletonRepository.remove(service);
    }
    
    public boolean containSingleton(Service service) {
        return singletonRepository.containsKey(service);
    }
    
    public int size() {
        return singletonRepository.size();
    }
}
