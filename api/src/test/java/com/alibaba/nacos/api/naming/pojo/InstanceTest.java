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

package com.alibaba.nacos.api.naming.pojo;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InstanceTest {
    
    private static ObjectMapper mapper;
    
    @BeforeClass
    public static void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    public void testSetAndGet() {
        Instance instance = new Instance();
        assertNull(instance.getInstanceId());
        assertNull(instance.getIp());
        assertEquals(0, instance.getPort());
        assertEquals(1.0D, instance.getWeight(), 0.1);
        assertTrue(instance.isHealthy());
        assertTrue(instance.isEnabled());
        assertTrue(instance.isEphemeral());
        assertNull(instance.getClusterName());
        assertNull(instance.getServiceName());
        assertTrue(instance.getMetadata().isEmpty());
        setInstance(instance);
        checkInstance(instance);
    }
    
    @Test
    public void testJsonSerialize() throws JsonProcessingException {
        Instance instance = new Instance();
        setInstance(instance);
        String actual = mapper.writeValueAsString(instance);
        assertTrue(actual.contains("\"instanceId\":\"id\""));
        assertTrue(actual.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(actual.contains("\"port\":1000"));
        assertTrue(actual.contains("\"weight\":100.0"));
        assertTrue(actual.contains("\"healthy\":false"));
        assertTrue(actual.contains("\"enabled\":false"));
        assertTrue(actual.contains("\"ephemeral\":false"));
        assertTrue(actual.contains("\"clusterName\":\"cluster\""));
        assertTrue(actual.contains("\"serviceName\":\"group@@serviceName\""));
        assertTrue(actual.contains("\"metadata\":{\"a\":\"b\"}"));
        assertTrue(actual.contains("\"instanceHeartBeatInterval\":5000"));
        assertTrue(actual.contains("\"instanceHeartBeatTimeOut\":15000"));
        assertTrue(actual.contains("\"ipDeleteTimeout\":30000"));
    }
    
    @Test
    public void testJsonDeserialize() throws JsonProcessingException {
        String json = "{\"instanceId\":\"id\",\"ip\":\"1.1.1.1\",\"port\":1000,\"weight\":100.0,\"healthy\":false,"
                + "\"enabled\":false,\"ephemeral\":false,\"clusterName\":\"cluster\","
                + "\"serviceName\":\"group@@serviceName\",\"metadata\":{\"a\":\"b\"},\"instanceHeartBeatInterval\":5000,"
                + "\"instanceHeartBeatTimeOut\":15000,\"ipDeleteTimeout\":30000}";
        Instance instance = mapper.readValue(json, Instance.class);
        checkInstance(instance);
    }
    
    @Test
    public void testCheckClusterNameFormat() {
        Instance instance = new Instance();
        instance.setClusterName("demo");
        assertEquals("demo", instance.getClusterName());
    }
    
    @Test
    public void testToInetAddr() {
        Instance instance = new Instance();
        setInstance(instance);
        assertEquals("1.1.1.1:1000", instance.toInetAddr());
    }
    
    @Test
    public void testContainsMetadata() {
        Instance instance = new Instance();
        assertFalse(instance.containsMetadata("a"));
        instance.setMetadata(null);
        assertFalse(instance.containsMetadata("a"));
        instance.addMetadata("a", "b");
        assertTrue(instance.containsMetadata("a"));
    }
    
    @Test
    public void testGetInstanceIdGenerator() {
        Instance instance = new Instance();
        assertEquals(Constants.DEFAULT_INSTANCE_ID_GENERATOR, instance.getInstanceIdGenerator());
        instance.addMetadata(PreservedMetadataKeys.INSTANCE_ID_GENERATOR, "test");
        assertEquals("test", instance.getInstanceIdGenerator());
    }
    
    @Test
    public void testEquals() {
        Instance actual = new Instance();
        setInstance(actual);
        actual.setMetadata(new HashMap<>());
        actual.addMetadata("a", "b");
        assertFalse(actual.equals(new Object()));
        Instance expected = new Instance();
        setInstance(expected);
        expected.setMetadata(new HashMap<>());
        expected.addMetadata("a", "b");
        assertTrue(actual.equals(expected));
        expected.addMetadata("a", "c");
        assertFalse(actual.equals(expected));
    }
    
    private void setInstance(Instance instance) {
        instance.setInstanceId("id");
        instance.setIp("1.1.1.1");
        instance.setPort(1000);
        instance.setWeight(100);
        instance.setHealthy(false);
        instance.setEnabled(false);
        instance.setEphemeral(false);
        instance.setClusterName("cluster");
        instance.setServiceName("group@@serviceName");
        instance.setMetadata(Collections.singletonMap("a", "b"));
    }
    
    private void checkInstance(Instance instance) {
        assertEquals("id", instance.getInstanceId());
        assertEquals("1.1.1.1", instance.getIp());
        assertEquals(1000, instance.getPort());
        assertEquals(100D, instance.getWeight(), 0.1);
        assertFalse(instance.isHealthy());
        assertFalse(instance.isEnabled());
        assertFalse(instance.isEphemeral());
        assertEquals("cluster", instance.getClusterName());
        assertEquals("group@@serviceName", instance.getServiceName());
        assertFalse(instance.getMetadata().isEmpty());
        assertTrue(instance.containsMetadata("a"));
        assertEquals("b", instance.getMetadata().get("a"));
    }
}