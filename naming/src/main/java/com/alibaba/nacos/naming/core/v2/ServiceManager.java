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

import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos service manager for v2.
 *
 * @author xiweng.yy
 */
public class ServiceManager {
    
    private static final ServiceManager INSTANCE = new ServiceManager();
    
    private final ConcurrentHashMap<Service, Service> singletonRepository;
    
    private ServiceManager() {
        singletonRepository = new ConcurrentHashMap<>(1 << 10);
    }
    
    public static ServiceManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Get singleton service.
     *
     * @param service new service
     * @return if service is exist, return exist service, otherwise return new service
     */
    public Service getSingleton(Service service) {
        Service previous = singletonRepository.putIfAbsent(service, service);
        return (null == previous) ? service : previous;
    }
    
    /**
     * Remove singleton service.
     *
     * @param service service need to remove
     * @return removed service
     */
    public Service removeSingleton(Service service) {
        return singletonRepository.remove(service);
    }
    
    public boolean containSingleton(Service service) {
        return singletonRepository.containsKey(service);
    }
    
    public int size() {
        return singletonRepository.size();
    }
}
