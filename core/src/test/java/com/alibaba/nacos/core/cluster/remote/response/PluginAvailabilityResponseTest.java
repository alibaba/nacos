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

package com.alibaba.nacos.core.cluster.remote.response;

import com.alibaba.nacos.common.remote.PayloadRegistry;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PluginAvailabilityResponse} unit test.
 *
 * @author WangzJi
 */
class PluginAvailabilityResponseTest {
    
    @BeforeAll
    static void initPayloadRegistry() {
        PayloadRegistry.init();
    }
    
    @Test
    void testSinglePluginPayloadRoundTrip() {
        PluginAvailabilityResponse response = new PluginAvailabilityResponse();
        response.setPluginId("auth:nacos");
        response.setAvailable(true);
        
        PluginAvailabilityResponse parsed =
            (PluginAvailabilityResponse) GrpcUtils.parse(GrpcUtils.convert(response));
        
        assertEquals("auth:nacos", parsed.getPluginId());
        assertTrue(parsed.isAvailable());
        assertTrue(parsed.isSuccess());
    }
    
    @Test
    void testQueryAllPayloadRoundTrip() {
        Map<String, Boolean> availability = new HashMap<>();
        availability.put("auth:nacos", true);
        availability.put("auth:ldap", false);
        PluginAvailabilityResponse response = new PluginAvailabilityResponse();
        response.setPluginAvailabilityMap(availability);
        
        PluginAvailabilityResponse parsed =
            (PluginAvailabilityResponse) GrpcUtils.parse(GrpcUtils.convert(response));
        
        assertEquals(availability, parsed.getPluginAvailabilityMap());
        assertTrue(parsed.isSuccess());
    }
    
    @Test
    void defaultConstructorTest() {
        PluginAvailabilityResponse response = new PluginAvailabilityResponse();
        
        assertNull(response.getPluginId());
        assertFalse(response.isAvailable());
    }
    
    @Test
    void setPluginIdTest() {
        PluginAvailabilityResponse response = new PluginAvailabilityResponse();
        
        response.setPluginId("trace:otel");
        
        assertEquals("trace:otel", response.getPluginId());
    }
    
    @Test
    void setAvailableTrueTest() {
        PluginAvailabilityResponse response = new PluginAvailabilityResponse();
        
        response.setAvailable(true);
        
        assertTrue(response.isAvailable());
    }
    
    @Test
    void setAvailableFalseTest() {
        PluginAvailabilityResponse response = new PluginAvailabilityResponse();
        
        response.setAvailable(true);
        response.setAvailable(false);
        
        assertFalse(response.isAvailable());
    }
    
    @Test
    void setPluginIdAndAvailableTest() {
        PluginAvailabilityResponse response = new PluginAvailabilityResponse();
        
        response.setPluginId("auth:nacos");
        response.setAvailable(true);
        
        assertEquals("auth:nacos", response.getPluginId());
        assertTrue(response.isAvailable());
    }
}
