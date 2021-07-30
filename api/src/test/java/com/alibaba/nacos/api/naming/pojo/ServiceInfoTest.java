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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServiceInfoTest {
    
    private ObjectMapper mapper;
    
    private ServiceInfo serviceInfo;
    
    @Before
    public void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        serviceInfo = new ServiceInfo("G@@testName", "testClusters");
    }
    
    @Test
    public void testSerialize() throws JsonProcessingException {
        String actual = mapper.writeValueAsString(serviceInfo);
        assertTrue(actual.contains("\"name\":\"G@@testName\""));
        assertTrue(actual.contains("\"clusters\":\"testClusters\""));
        assertTrue(actual.contains("\"cacheMillis\":1000"));
        assertTrue(actual.contains("\"hosts\":[]"));
        assertTrue(actual.contains("\"lastRefTime\":0"));
        assertTrue(actual.contains("\"checksum\":\"\""));
        assertTrue(actual.contains("\"valid\":true"));
        assertTrue(actual.contains("\"allIPs\":false"));
        assertFalse(actual.contains("jsonFromServer"));
        assertFalse(actual.contains("key"));
        assertFalse(actual.contains("keyEncoded"));
    }
    
    @Test
    public void testDeserialize() throws IOException {
        String example = "{\"name\":\"G@@testName\",\"clusters\":\"testClusters\",\"cacheMillis\":1000,\"hosts\":[],"
                + "\"lastRefTime\":0,\"checksum\":\"\",\"allIPs\":false,\"valid\":true,\"groupName\":\"\"}";
        ServiceInfo actual = mapper.readValue(example, ServiceInfo.class);
        assertEquals("G@@testName", actual.getName());
        assertEquals("testClusters", actual.getClusters());
        assertEquals("", actual.getChecksum());
        assertEquals("", actual.getGroupName());
        assertEquals(1000, actual.getCacheMillis());
        assertEquals(0, actual.getLastRefTime());
        assertTrue(actual.getHosts().isEmpty());
        assertTrue(actual.isValid());
        assertFalse(actual.isAllIPs());
    }
    
    @Test
    public void testGetKey() {
        String key = serviceInfo.getKey();
        assertEquals("G@@testName@@testClusters", key);
    }
    
    @Test
    public void testGetKeyEncode() {
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
    public void testServiceInfoConstructor() {
        String key1 = "group@@name";
        String key2 = "group@@name@@c2";
        ServiceInfo s1 = new ServiceInfo(key1);
        ServiceInfo s2 = new ServiceInfo(key2);
        assertEquals(key1, s1.getKey());
        assertEquals(key2, s2.getKey());
    }
}
