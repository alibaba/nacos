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

package com.alibaba.nacos.naming.core.v2.cleaner;

import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Empty Service Auto Cleaner.
 *
 * @author xiweng.yy
 */
public class EmptyServiceAutoCleaner extends AbstractNamingCleaner {
    
    private static final int MAX_FINALIZE_COUNT = 3;
    
    private final ServiceManager serviceManager;
    
    private final DistroMapper distroMapper;
    
    public EmptyServiceAutoCleaner(ServiceManager serviceManager, DistroMapper distroMapper) {
        this.serviceManager = serviceManager;
        this.distroMapper = distroMapper;
    }
    
    @Override
    public void run() {
        
        // Parallel flow opening threshold
        int parallelSize = 100;
        
        for (String each : serviceManager.getAllNamespaces()) {
            Map<String, Service> serviceMap = serviceManager.chooseServiceMap(each);
            
            Stream<Map.Entry<String, Service>> stream;
            if (serviceMap.size() > parallelSize) {
                stream = serviceMap.entrySet().parallelStream();
            } else {
                stream = serviceMap.entrySet().stream();
            }
            stream.filter(entry -> {
                final String serviceName = entry.getKey();
                return distroMapper.responsible(serviceName);
            }).forEach(entry -> serviceMap.computeIfPresent(entry.getKey(), (serviceName, service) -> {
                if (service.isEmpty()) {
                    
                    // To avoid violent Service removal, the number of times the Service
                    // experiences Empty is determined by finalizeCnt, and if the specified
                    // value is reached, it is removed
                    
                    if (service.getFinalizeCount() > MAX_FINALIZE_COUNT) {
                        Loggers.SRV_LOG
                                .warn("namespace : {}, [{}] services are automatically cleaned", each, serviceName);
                        try {
                            serviceManager.easyRemoveService(each, serviceName);
                        } catch (Exception e) {
                            Loggers.SRV_LOG
                                    .error("namespace : {}, [{}] services are automatically clean has " + "error : {}",
                                            each, serviceName, e);
                        }
                    }
                    
                    service.setFinalizeCount(service.getFinalizeCount() + 1);
                    
                    Loggers.SRV_LOG.debug("namespace : {}, [{}] The number of times the current service experiences "
                            + "an empty instance is : {}", each, serviceName, service.getFinalizeCount());
                } else {
                    service.setFinalizeCount(0);
                }
                return service;
            }));
        }
    }
    
    @Override
    public String getType() {
        return null;
    }
    
    @Override
    public void doClean() {
    
    }
}
