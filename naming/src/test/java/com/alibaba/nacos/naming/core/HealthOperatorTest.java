/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthOperatorTest {
    
    @Test
    void testDefaultUpdateParsesFullServiceName() throws NacosException {
        RecordingHealthOperator healthOperator = new RecordingHealthOperator();
        
        healthOperator.updateHealthStatusForPersistentInstance("namespace", "group@@service",
            "cluster", "1.1.1.1", 8848, true);
        
        assertEquals("namespace", healthOperator.namespace);
        assertEquals("group", healthOperator.groupName);
        assertEquals("service", healthOperator.serviceName);
        assertEquals("cluster", healthOperator.clusterName);
        assertEquals("1.1.1.1", healthOperator.ip);
        assertEquals(8848, healthOperator.port);
        assertEquals(true, healthOperator.healthy);
    }
    
    private static class RecordingHealthOperator implements HealthOperator {
        
        private String namespace;
        
        private String groupName;
        
        private String serviceName;
        
        private String clusterName;
        
        private String ip;
        
        private int port;
        
        private boolean healthy;
        
        @Override
        public void updateHealthStatusForPersistentInstance(String namespace, String groupName,
            String serviceName,
            String clusterName, String ip, int port, boolean healthy) {
            this.namespace = namespace;
            this.groupName = groupName;
            this.serviceName = serviceName;
            this.clusterName = clusterName;
            this.ip = ip;
            this.port = port;
            this.healthy = healthy;
        }
        
        @Override
        public Map<String, AbstractHealthChecker> checkers() {
            return Collections.emptyMap();
        }
    }
}
