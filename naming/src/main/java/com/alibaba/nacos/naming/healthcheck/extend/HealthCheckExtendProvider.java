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

package com.alibaba.nacos.naming.healthcheck.extend;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Health check extend provider.
 *
 * @author XCXCXCXCX
 */
@Component
public class HealthCheckExtendProvider {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckExtendProvider.class);

    private final Collection<AbstractHealthChecker> checkers = NacosServiceLoader.load(AbstractHealthChecker.class);

    private AbstractHealthCheckProcessorExtend healthCheckProcessorExtend;

    public void setHealthCheckProcessorExtend(AbstractHealthCheckProcessorExtend healthCheckProcessorExtend) {
        this.healthCheckProcessorExtend = healthCheckProcessorExtend;
    }

    public void init() {
        loadExtend();
    }
    
    private void loadExtend() {
        Iterator<AbstractHealthChecker> healthCheckerIt = checkers.iterator();
        
        Set<String> origin = new HashSet<>();
        for (HealthCheckType type : HealthCheckType.values()) {
            origin.add(type.name());
        }
        Set<String> processorType = healthCheckProcessorExtend.addProcessor(origin);
        Set<String> healthCheckerType = new HashSet<>(origin);
        
        while (healthCheckerIt.hasNext()) {
            AbstractHealthChecker checker = healthCheckerIt.next();
            String type = checker.getType();
            if (healthCheckerType.contains(type)) {
                throw new RuntimeException(
                        "More than one healthChecker of the same type was found : [type=\"" + type + "\"]");
            }
            healthCheckerType.add(type);
            HealthCheckType.registerHealthChecker(checker.getType(), checker.getClass());
        }
        if (!processorType.equals(healthCheckerType)) {
            throw new RuntimeException(
                    "An unmatched processor and healthChecker are detected in the extension package.");
        }
        if (processorType.size() > origin.size()) {
            processorType.removeAll(origin);
            LOGGER.debug("init health plugin : types=" + processorType);
        }
    }
}
