/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.query.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigQueryChainRequestTest {
    
    @Test
    void testSettersAndGetters() {
        ConfigQueryChainRequest req = new ConfigQueryChainRequest();
        req.setDataId("dataId");
        req.setGroup("group");
        req.setTenant("tenant");
        req.setTag("tag");
        Map<String, String> labels = new HashMap<>();
        labels.put("k", "v");
        req.setAppLabels(labels);
        
        assertEquals("dataId", req.getDataId());
        assertEquals("group", req.getGroup());
        assertEquals("tenant", req.getTenant());
        assertEquals("tag", req.getTag());
        assertEquals(labels, req.getAppLabels());
    }
    
    @Test
    void testBuildConfigQueryChainRequest() {
        ConfigQueryChainRequest req =
            ConfigQueryChainRequest.buildConfigQueryChainRequest(
                "dataId", "group", "ns");
        assertNotNull(req);
        assertEquals("dataId", req.getDataId());
        assertEquals("group", req.getGroup());
        assertEquals("ns", req.getTenant());
        assertNull(req.getTag());
    }
    
    @Test
    void testEquals() {
        ConfigQueryChainRequest a =
            ConfigQueryChainRequest.buildConfigQueryChainRequest("d", "g", "t");
        ConfigQueryChainRequest b =
            ConfigQueryChainRequest.buildConfigQueryChainRequest("d", "g", "t");
        assertEquals(a, b);
    }
    
    @Test
    void testEqualsSelf() {
        ConfigQueryChainRequest a = new ConfigQueryChainRequest();
        assertEquals(a, a);
    }
    
    @Test
    void testNotEqualsNull() {
        ConfigQueryChainRequest a = new ConfigQueryChainRequest();
        assertFalse(a.equals(null));
    }
    
    @Test
    void testNotEqualsDifferentClass() {
        ConfigQueryChainRequest a = new ConfigQueryChainRequest();
        assertFalse(a.equals("str"));
    }
    
    @Test
    void testNotEqualsDifferent() {
        ConfigQueryChainRequest a =
            ConfigQueryChainRequest.buildConfigQueryChainRequest("d1", "g", "t");
        ConfigQueryChainRequest b =
            ConfigQueryChainRequest.buildConfigQueryChainRequest("d2", "g", "t");
        assertNotEquals(a, b);
    }
    
    @Test
    void testHashCode() {
        ConfigQueryChainRequest a =
            ConfigQueryChainRequest.buildConfigQueryChainRequest("d", "g", "t");
        ConfigQueryChainRequest b =
            ConfigQueryChainRequest.buildConfigQueryChainRequest("d", "g", "t");
        assertEquals(a.hashCode(), b.hashCode());
    }
}
