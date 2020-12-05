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

package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.naming.healthcheck.extend.HealthCheckExtendProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Delegate of health check.
 *
 * @author nacos
 */
@Component("healthCheckDelegate")
public class HealthCheckProcessorDelegate implements HealthCheckProcessor {
    
    private Map<String, HealthCheckProcessor> healthCheckProcessorMap = new HashMap<>();
    
    public HealthCheckProcessorDelegate(HealthCheckExtendProvider provider) {
        provider.init();
    }
    
    @Autowired
    public void addProcessor(Collection<HealthCheckProcessor> processors) {
        healthCheckProcessorMap.putAll(processors.stream().filter(processor -> processor.getType() != null)
                .collect(Collectors.toMap(HealthCheckProcessor::getType, processor -> processor)));
    }
    
    @Override
    public void process(HealthCheckTask task) {
        
        String type = task.getCluster().getHealthChecker().getType();
        HealthCheckProcessor processor = healthCheckProcessorMap.get(type);
        if (processor == null) {
            processor = healthCheckProcessorMap.get(NoneHealthCheckProcessor.TYPE);
        }
        
        processor.process(task);
    }
    
    @Override
    public String getType() {
        return null;
    }
}
