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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.NoneHealthCheckProcessor;
import com.alibaba.nacos.naming.healthcheck.extend.HealthCheckExtendProvider;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Delegate of health check v2.x.
 *
 * @author nacos
 */
@Component("healthCheckDelegateV2")
public class HealthCheckProcessorV2Delegate implements HealthCheckProcessorV2 {
    
    private final Map<String, HealthCheckProcessorV2> healthCheckProcessorMap = new HashMap<>();
    
    public HealthCheckProcessorV2Delegate(HealthCheckExtendProvider provider) {
        provider.init();
    }
    
    @Autowired
    public void addProcessor(Collection<HealthCheckProcessorV2> processors) {
        healthCheckProcessorMap.putAll(processors.stream().filter(processor -> processor.getType() != null)
                .collect(Collectors.toMap(HealthCheckProcessorV2::getType, processor -> processor)));
    }
    
    @Override
    public void process(HealthCheckTaskV2 task, Service service, ClusterMetadata metadata) {
        String type = metadata.getHealthyCheckType();
        HealthCheckProcessorV2 processor = healthCheckProcessorMap.get(type);
        if (processor == null) {
            processor = healthCheckProcessorMap.get(NoneHealthCheckProcessor.TYPE);
        }
        processor.process(task, service, metadata);
    }
    
    @Override
    public String getType() {
        return null;
    }
}
