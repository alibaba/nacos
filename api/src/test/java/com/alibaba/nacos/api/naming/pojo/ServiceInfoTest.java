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

import static org.junit.Assert.*;

public class ServiceInfoTest {

    private ObjectMapper mapper;

    private ServiceInfo serviceInfo;

    @Before
    public void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        serviceInfo = new ServiceInfo("testName", "testClusters");
    }

    @Test
    public void testSerialize() throws JsonProcessingException {
        String actual = mapper.writeValueAsString(serviceInfo);
        assertTrue(actual.contains("\"name\":\"testName\""));
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
        String example = "{\"name\":\"testName\",\"clusters\":\"testClusters\",\"cacheMillis\":1000,\"hosts\":[],\"lastRefTime\":0,\"checksum\":\"\",\"allIPs\":false,\"valid\":true,\"groupName\":\"\"}";
        ServiceInfo actual = mapper.readValue(example, ServiceInfo.class);
        assertEquals("testName", actual.getName());
        assertEquals("testClusters", actual.getClusters());
        assertEquals("", actual.getChecksum());
        assertEquals("", actual.getGroupName());
        assertEquals(1000, actual.getCacheMillis());
        assertEquals(0, actual.getLastRefTime());
        assertTrue(actual.getHosts().isEmpty());
        assertTrue(actual.isValid());
        assertFalse(actual.isAllIPs());
    }
}
