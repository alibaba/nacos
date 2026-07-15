/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.pojo.InstanceOperationInfo;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.pojo.instance.BeatInfoInstanceBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class InstanceOperatorTest {
    
    @Test
    @SuppressWarnings("deprecation")
    void testDeprecatedDefaultMethodsDelegateWithParsedServiceName() throws Exception {
        CapturingInstanceOperator operator = new CapturingInstanceOperator();
        Instance instance = new Instance();
        InstancePatchObject patchObject = new InstancePatchObject("cluster", "1.1.1.1", 8848);
        Subscriber subscriber = new Subscriber();
        
        operator.removeInstance("namespace", "group@@service", instance);
        assertDelegated(operator, "remove", "namespace", "group", "service");
        assertSame(instance, operator.instance);
        
        operator.updateInstance("namespace", "group@@service", instance);
        assertDelegated(operator, "update", "namespace", "group", "service");
        assertSame(instance, operator.instance);
        
        operator.patchInstance("namespace", "group@@service", patchObject);
        assertDelegated(operator, "patch", "namespace", "group", "service");
        assertSame(patchObject, operator.patchObject);
        
        ServiceInfo actualServiceInfo = operator.listInstance("namespace", "group@@service",
            subscriber, "cluster", true);
        assertSame(operator.serviceInfo, actualServiceInfo);
        assertDelegated(operator, "list", "namespace", "group", "service");
        assertSame(subscriber, operator.subscriber);
        assertEquals("cluster", operator.cluster);
        assertEquals(true, operator.healthOnly);
        
        Instance actualInstance = operator.getInstance("namespace", "group@@service", "cluster",
            "1.1.1.1", 8848);
        assertSame(operator.instance, actualInstance);
        assertDelegated(operator, "get", "namespace", "group", "service");
        assertEquals("cluster", operator.cluster);
        assertEquals("1.1.1.1", operator.ip);
        assertEquals(8848, operator.port);
    }
    
    private void assertDelegated(CapturingInstanceOperator operator, String operation,
        String namespaceId, String groupName, String serviceName) {
        assertEquals(operation, operator.operation);
        assertEquals(namespaceId, operator.namespaceId);
        assertEquals(groupName, operator.groupName);
        assertEquals(serviceName, operator.serviceName);
    }
    
    private static class CapturingInstanceOperator implements InstanceOperator {
        
        private final ServiceInfo serviceInfo = new ServiceInfo("group@@service");
        
        private String operation;
        
        private String namespaceId;
        
        private String groupName;
        
        private String serviceName;
        
        private Instance instance;
        
        private InstancePatchObject patchObject;
        
        private Subscriber subscriber;
        
        private String cluster;
        
        private boolean healthOnly;
        
        private String ip;
        
        private int port;
        
        @Override
        public void registerInstance(String namespaceId, String groupName, String serviceName,
            Instance instance) {
        }
        
        @Override
        public void removeInstance(String namespaceId, String groupName, String serviceName,
            Instance instance) {
            capture("remove", namespaceId, groupName, serviceName);
            this.instance = instance;
        }
        
        @Override
        public void updateInstance(String namespaceId, String groupName, String serviceName,
            Instance instance) {
            capture("update", namespaceId, groupName, serviceName);
            this.instance = instance;
        }
        
        @Override
        public void patchInstance(String namespaceId, String groupName, String serviceName,
            InstancePatchObject patchObject) {
            capture("patch", namespaceId, groupName, serviceName);
            this.patchObject = patchObject;
        }
        
        @Override
        public ServiceInfo listInstance(String namespaceId, String groupName, String serviceName,
            Subscriber subscriber, String cluster, boolean healthOnly) {
            capture("list", namespaceId, groupName, serviceName);
            this.subscriber = subscriber;
            this.cluster = cluster;
            this.healthOnly = healthOnly;
            return serviceInfo;
        }
        
        @Override
        public Instance getInstance(String namespaceId, String groupName, String serviceName,
            String cluster, String ip, int port) {
            capture("get", namespaceId, groupName, serviceName);
            this.cluster = cluster;
            this.ip = ip;
            this.port = port;
            return instance;
        }
        
        @Override
        public int handleBeat(String namespaceId, String groupName, String serviceName, String ip,
            int port, String cluster, RsInfo clientBeat, BeatInfoInstanceBuilder builder) {
            return 0;
        }
        
        @Override
        public long getHeartBeatInterval(String namespaceId, String serviceName, String ip,
            int port, String cluster) {
            return 0;
        }
        
        @Override
        public List<? extends Instance> listAllInstances(String namespaceId, String serviceName) {
            return List.of();
        }
        
        @Override
        public List<String> batchUpdateMetadata(String namespaceId,
            InstanceOperationInfo instanceOperationInfo, Map<String, String> metadata) {
            return List.of();
        }
        
        @Override
        public List<String> batchDeleteMetadata(String namespaceId,
            InstanceOperationInfo instanceOperationInfo, Map<String, String> metadata) {
            return List.of();
        }
        
        private void capture(String operation, String namespaceId, String groupName,
            String serviceName) {
            this.operation = operation;
            this.namespaceId = namespaceId;
            this.groupName = groupName;
            this.serviceName = serviceName;
        }
    }
}
