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

package com.alibaba.nacos.api.naming.pojo.builder;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceBuilderTest {
    
    private static final String SERVICE_NAME = "testService";
    
    private static final String CLUSTER_NAME = "testCluster";
    
    private static final String INSTANCE_ID = "ID";
    
    private static final String IP = "127.0.0.1";
    
    private static final int PORT = 8848;
    
    private static final double WEIGHT = 2.0;
    
    private static final boolean HEALTHY = false;
    
    private static final boolean ENABLED = false;
    
    private static final boolean EPHEMERAL = false;
    
    private static final String META_KEY = "key";
    
    private static final String META_VALUE = "value";
    
    @Test
    void testBuildFullInstance() {
        InstanceBuilder builder = InstanceBuilder.newBuilder();
        Instance actual = builder.setServiceName(SERVICE_NAME).setClusterName(CLUSTER_NAME).setInstanceId(INSTANCE_ID).setIp(IP)
                .setPort(PORT).setWeight(WEIGHT).setHealthy(HEALTHY).setEnabled(ENABLED).setEphemeral(EPHEMERAL)
                .addMetadata(META_KEY, META_VALUE).build();
        assertEquals(actual.getServiceName(), SERVICE_NAME);
        assertEquals(actual.getClusterName(), CLUSTER_NAME);
        assertEquals(actual.getInstanceId(), INSTANCE_ID);
        assertEquals(actual.getIp(), IP);
        assertEquals(actual.getPort(), PORT);
        assertEquals(actual.getWeight(), WEIGHT);
        assertEquals(actual.isHealthy(), HEALTHY);
        assertEquals(actual.isEnabled(), ENABLED);
        assertEquals(actual.isEphemeral(), EPHEMERAL);
        assertEquals(actual.getMetadata().size(), 1);
        assertEquals(actual.getMetadata().get(META_KEY), META_VALUE);
    }
    
    @Test
    void testBuildInstanceWithoutNewMetadata() {
        InstanceBuilder builder = InstanceBuilder.newBuilder();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("test", "test");
        Instance actual = builder.setMetadata(metadata).build();
        assertNull(actual.getServiceName());
        assertNull(actual.getClusterName());
        assertNull(actual.getInstanceId());
        assertNull(actual.getIp());
        assertEquals(actual.getPort(), 0);
        assertEquals(actual.getWeight(), 1.0);
        assertTrue(actual.isHealthy());
        assertTrue(actual.isEnabled());
        assertTrue(actual.isEphemeral());
        assertEquals(1, actual.getMetadata().size());
    }
    
    @Test
    void testBuildEmptyInstance() {
        InstanceBuilder builder = InstanceBuilder.newBuilder();
        Instance actual = builder.build();
        assertNull(actual.getServiceName());
        assertNull(actual.getClusterName());
        assertNull(actual.getInstanceId());
        assertNull(actual.getIp());
        assertEquals(actual.getPort(), 0);
        assertEquals(actual.getWeight(), 1.0);
        assertTrue(actual.isHealthy());
        assertTrue(actual.isEnabled());
        assertTrue(actual.isEphemeral());
        assertTrue(actual.getMetadata().isEmpty());
    }
}
