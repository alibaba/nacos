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

package com.alibaba.nacos.api.naming.pojo;

import com.alibaba.nacos.api.utils.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceInfoTest {
    
    private ObjectMapper mapper;
    
    private ServiceInfo serviceInfo;
    
    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        serviceInfo = new ServiceInfo("G@@testName", "testClusters");
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        String actual = mapper.writeValueAsString(serviceInfo);
        assertTrue(actual.contains("\"name\":\"G@@testName\""));
        assertTrue(actual.contains("\"clusters\":\"testClusters\""));
        assertTrue(actual.contains("\"cacheMillis\":1000"));
        assertTrue(actual.contains("\"hosts\":[]"));
        assertTrue(actual.contains("\"lastRefTime\":0"));
        assertTrue(actual.contains("\"checksum\":\"\""));
        assertTrue(actual.contains("\"valid\":true"));
        assertTrue(actual.contains("\"allIps\":false"));
        assertFalse(actual.contains("jsonFromServer"));
        assertFalse(actual.contains("key"));
        assertFalse(actual.contains("keyEncoded"));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String example = "{\"name\":\"G@@testName\",\"clusters\":\"testClusters\",\"cacheMillis\":1000,\"hosts\":[],"
                + "\"lastRefTime\":0,\"checksum\":\"\",\"allIps\":false,\"valid\":true,\"groupName\":\"\"}";
        ServiceInfo actual = mapper.readValue(example, ServiceInfo.class);
        assertEquals("G@@testName", actual.getName());
        assertEquals(0, actual.ipCount());
        assertEquals("testClusters", actual.getClusters());
        assertEquals("", actual.getChecksum());
        assertEquals("", actual.getGroupName());
        assertEquals(1000, actual.getCacheMillis());
        assertEquals(0, actual.getLastRefTime());
        assertTrue(actual.expired());
        assertTrue(actual.getHosts().isEmpty());
        assertTrue(actual.isValid());
        assertFalse(actual.isAllIps());
    }
    
    @Test
    void testGetKey() {
        String key = serviceInfo.getKey();
        assertEquals("G@@testName@@testClusters", key);
        assertEquals("G@@testName@@testClusters", serviceInfo.toString());
    }
    
    @Test
    void testGetKeyEncode() {
        String key = serviceInfo.getKeyEncoded();
        String encodeName = null;
        try {
            encodeName = URLEncoder.encode("G@@testName", "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        assertEquals(key, ServiceInfo.getKey(encodeName, "testClusters"));
    }
    
    @Test
    void testGetKeyWithException() {
        try (MockedStatic<URLEncoder> mockedStatic = Mockito.mockStatic(URLEncoder.class)) {
            mockedStatic.when(() -> URLEncoder.encode(Mockito.anyString(), Mockito.anyString()))
                    .thenThrow(new UnsupportedEncodingException());
            String key = serviceInfo.getKeyEncoded();
            assertEquals(key, ServiceInfo.getKey("G@@testName", "testClusters"));
        }
    }
    
    @Test
    void testServiceInfoConstructor() {
        String key1 = "group@@name";
        String key2 = "group@@name@@c2";
        ServiceInfo s1 = new ServiceInfo(key1);
        ServiceInfo s2 = new ServiceInfo(key2);
        assertEquals(key1, s1.getKey());
        assertEquals(key2, s2.getKey());
    }
    
    @Test
    void testServiceInfoConstructorWithError() {
        assertThrows(IllegalArgumentException.class, () -> {
            String key1 = "name";
            ServiceInfo s1 = new ServiceInfo(key1);
        });
    }
    
    @Test
    void testValidateForAllIps() {
        serviceInfo.setAllIps(true);
        assertTrue(serviceInfo.validate());
    }
    
    @Test
    void testValidateForNullHosts() {
        serviceInfo.setHosts(null);
        assertFalse(serviceInfo.validate());
    }
    
    @Test
    void testValidateForEmptyHosts() {
        serviceInfo.setHosts(Collections.EMPTY_LIST);
        assertFalse(serviceInfo.validate());
    }
    
    @Test
    void testValidateForUnhealthyHosts() {
        Instance instance = new Instance();
        instance.setHealthy(false);
        serviceInfo.addHost(instance);
        assertFalse(serviceInfo.validate());
    }
    
    @Test
    void testValidateForBothUnhealthyAndHealthyHosts() {
        List<Instance> instanceList = new LinkedList<>();
        Instance instance = new Instance();
        instanceList.add(instance);
        instance = new Instance();
        instance.setHealthy(false);
        instanceList.add(instance);
        serviceInfo.addAllHosts(instanceList);
        assertTrue(serviceInfo.validate());
    }
    
    @Test
    void testFromKey() {
        String key1 = "group@@name";
        String key2 = "group@@name@@c2";
        ServiceInfo s1 = ServiceInfo.fromKey(key1);
        ServiceInfo s2 = ServiceInfo.fromKey(key2);
        assertEquals(key1, s1.getKey());
        assertEquals(key2, s2.getKey());
    }
    
    @Test
    void testSetAndGet() throws JsonProcessingException {
        serviceInfo.setReachProtectionThreshold(true);
        serviceInfo.setJsonFromServer(mapper.writeValueAsString(serviceInfo));
        ServiceInfo actual = mapper.readValue(serviceInfo.getJsonFromServer(), ServiceInfo.class);
        assertEquals(StringUtils.EMPTY, actual.getJsonFromServer());
        assertTrue(actual.isReachProtectionThreshold());
    }
    
    @Test
    void testGetKeyWithoutClusters() {
        // 测试带groupName的情况
        ServiceInfo serviceInfo1 = new ServiceInfo("group@@name", "cluster");
        assertEquals("group@@name", serviceInfo1.getKeyWithoutClusters());
        
        // 测试不带groupName的情况
        ServiceInfo serviceInfo2 = new ServiceInfo("name", "cluster");
        assertEquals("name", serviceInfo2.getKeyWithoutClusters());
        
        // 测试name中已经包含@@的情况
        ServiceInfo serviceInfo3 = new ServiceInfo("group@@name@@cluster", "");
        assertEquals("group@@name@@cluster", serviceInfo3.getKeyWithoutClusters());
    }
    
    @Test
    void testCloneBasicFields() {
        // Setup original ServiceInfo with all fields
        ServiceInfo original = new ServiceInfo("testGroup@@testName", "testClusters");
        original.setCacheMillis(2000L);
        original.setLastRefTime(1234567890L);
        original.setChecksum("testChecksum");
        original.setAllIps(true);
        original.setReachProtectionThreshold(true);
        original.setJsonFromServer("testJson");
        
        // Clone
        ServiceInfo cloned = original.clone();
        
        // Verify it's a different object
        assertNotSame(original, cloned);
        
        // Verify all basic fields are copied
        assertEquals(original.getName(), cloned.getName());
        assertEquals(original.getGroupName(), cloned.getGroupName());
        assertEquals(original.getClusters(), cloned.getClusters());
        assertEquals(original.getCacheMillis(), cloned.getCacheMillis());
        assertEquals(original.getLastRefTime(), cloned.getLastRefTime());
        assertEquals(original.getChecksum(), cloned.getChecksum());
        assertEquals(original.isAllIps(), cloned.isAllIps());
        assertEquals(original.isReachProtectionThreshold(), cloned.isReachProtectionThreshold());
        assertEquals(original.getJsonFromServer(), cloned.getJsonFromServer());
    }
    
    @Test
    void testCloneWithNullHosts() {
        final ServiceInfo original = new ServiceInfo("testGroup@@testName", "testClusters");
        original.setHosts(null);
        
        ServiceInfo cloned = original.clone();
        
        assertNotSame(original, cloned);
        // Clone method initializes hosts to empty list even if original is null
        assertTrue(cloned.getHosts().isEmpty());
    }
    
    @Test
    void testCloneWithEmptyHosts() {
        ServiceInfo original = new ServiceInfo("testGroup@@testName", "testClusters");
        original.setHosts(new LinkedList<>());
        
        ServiceInfo cloned = original.clone();
        
        assertNotSame(original, cloned);
        assertNotSame(original.getHosts(), cloned.getHosts());
        assertTrue(cloned.getHosts().isEmpty());
    }
    
    @Test
    void testCloneWithHosts() {
        // Setup original ServiceInfo with hosts
        final ServiceInfo original = new ServiceInfo("testGroup@@testName", "testClusters");
        
        Instance instance1 = new Instance();
        instance1.setInstanceId("instance1");
        instance1.setIp("192.168.1.1");
        instance1.setPort(8080);
        instance1.setWeight(1.0);
        instance1.setHealthy(true);
        instance1.setEnabled(true);
        instance1.setEphemeral(true);
        instance1.setClusterName("cluster1");
        instance1.setServiceName("service1");
        
        Instance instance2 = new Instance();
        instance2.setInstanceId("instance2");
        instance2.setIp("192.168.1.2");
        instance2.setPort(8081);
        instance2.setWeight(2.0);
        instance2.setHealthy(false);
        instance2.setEnabled(false);
        instance2.setEphemeral(false);
        instance2.setClusterName("cluster2");
        instance2.setServiceName("service2");
        
        original.addHost(instance1);
        original.addHost(instance2);
        
        // Clone
        ServiceInfo cloned = original.clone();
        
        // Verify it's a different object
        assertNotSame(original, cloned);
        
        // Verify hosts list is different
        assertNotSame(original.getHosts(), cloned.getHosts());
        assertEquals(original.getHosts().size(), cloned.getHosts().size());
        
        // Verify each host is a different object but with same values
        for (int i = 0; i < original.getHosts().size(); i++) {
            Instance originalHost = original.getHosts().get(i);
            Instance clonedHost = cloned.getHosts().get(i);
            
            assertNotSame(originalHost, clonedHost);
            assertEquals(originalHost.getInstanceId(), clonedHost.getInstanceId());
            assertEquals(originalHost.getIp(), clonedHost.getIp());
            assertEquals(originalHost.getPort(), clonedHost.getPort());
            assertEquals(originalHost.getWeight(), clonedHost.getWeight());
            assertEquals(originalHost.isHealthy(), clonedHost.isHealthy());
            assertEquals(originalHost.isEnabled(), clonedHost.isEnabled());
            assertEquals(originalHost.isEphemeral(), clonedHost.isEphemeral());
            assertEquals(originalHost.getClusterName(), clonedHost.getClusterName());
            assertEquals(originalHost.getServiceName(), clonedHost.getServiceName());
        }
    }
    
    @Test
    void testCloneWithHostsMetadata() {
        final ServiceInfo original = new ServiceInfo("testGroup@@testName", "testClusters");
        
        Instance instance = new Instance();
        instance.setIp("192.168.1.1");
        instance.setPort(8080);
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        metadata.put("key2", "value2");
        instance.setMetadata(metadata);
        
        original.addHost(instance);
        
        // Clone
        ServiceInfo cloned = original.clone();
        
        // Verify metadata is deep copied
        Instance originalHost = original.getHosts().get(0);
        Instance clonedHost = cloned.getHosts().get(0);
        
        assertNotSame(originalHost.getMetadata(), clonedHost.getMetadata());
        assertEquals(originalHost.getMetadata(), clonedHost.getMetadata());
        assertEquals(originalHost.getMetadata().size(), clonedHost.getMetadata().size());
        assertEquals("value1", clonedHost.getMetadata().get("key1"));
        assertEquals("value2", clonedHost.getMetadata().get("key2"));
    }
    
    @Test
    void testCloneWithHostsNullMetadata() {
        final ServiceInfo original = new ServiceInfo("testGroup@@testName", "testClusters");
        
        Instance instance = new Instance();
        instance.setIp("192.168.1.1");
        instance.setPort(8080);
        instance.setMetadata(null);
        
        original.addHost(instance);
        
        ServiceInfo cloned = original.clone();
        
        Instance clonedHost = cloned.getHosts().get(0);
        // Instance metadata is initialized to empty HashMap by default
        // When clone method doesn't set metadata (because original is null),
        // the cloned Instance keeps its default empty HashMap
        assertTrue(clonedHost.getMetadata() != null && clonedHost.getMetadata().isEmpty());
    }
    
    @Test
    void testCloneModificationDoesNotAffectOriginal() {
        ServiceInfo original = new ServiceInfo("testGroup@@testName", "testClusters");
        original.setCacheMillis(1000L);
        original.setAllIps(false);
        
        Instance instance = new Instance();
        instance.setIp("192.168.1.1");
        instance.setPort(8080);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        instance.setMetadata(metadata);
        original.addHost(instance);
        
        // Clone
        ServiceInfo cloned = original.clone();
        
        // Modify cloned object
        cloned.setCacheMillis(2000L);
        cloned.setAllIps(true);
        cloned.setName("modifiedName");
        cloned.getHosts().get(0).setIp("10.0.0.1");
        cloned.getHosts().get(0).getMetadata().put("key2", "value2");
        
        // Verify original is not affected
        assertEquals(1000L, original.getCacheMillis());
        assertFalse(original.isAllIps());
        assertEquals("testGroup@@testName", original.getName());
        assertEquals("192.168.1.1", original.getHosts().get(0).getIp());
        assertEquals(1, original.getHosts().get(0).getMetadata().size());
        assertFalse(original.getHosts().get(0).getMetadata().containsKey("key2"));
    }
}
