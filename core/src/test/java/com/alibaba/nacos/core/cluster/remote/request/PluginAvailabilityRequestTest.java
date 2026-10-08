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

package com.alibaba.nacos.core.cluster.remote.request;

import com.alibaba.nacos.common.remote.PayloadRegistry;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PluginAvailabilityRequest} unit test.
 *
 * @author WangzJi
 */
class PluginAvailabilityRequestTest {
    
    @BeforeAll
    static void initPayloadRegistry() {
        PayloadRegistry.init();
    }
    
    @Test
    void testSinglePluginPayloadRoundTrip() {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        request.setPluginId("auth:nacos");
        request.putHeader("test-server-identity", "test-value");
        
        PluginAvailabilityRequest parsed =
            (PluginAvailabilityRequest) GrpcUtils.parse(GrpcUtils.convert(request));
        
        assertEquals("auth:nacos", parsed.getPluginId());
        assertFalse(parsed.isQueryAll());
        assertEquals("test-value", parsed.getHeader("test-server-identity"));
    }
    
    @Test
    void testQueryAllPayloadRoundTrip() {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        request.setQueryAll(true);
        
        PluginAvailabilityRequest parsed =
            (PluginAvailabilityRequest) GrpcUtils.parse(GrpcUtils.convert(request));
        
        assertTrue(parsed.isQueryAll());
        assertNull(parsed.getPluginId());
    }
    
    @Test
    void defaultConstructorTest() {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        
        assertNull(request.getPluginId());
    }
    
    @Test
    void setPluginIdTest() {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        
        request.setPluginId("trace:otel");
        
        assertEquals("trace:otel", request.getPluginId());
    }
    
    @Test
    void setPluginIdMultipleTimesTest() {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        
        request.setPluginId("trace:otel");
        request.setPluginId("auth:nacos");
        
        assertEquals("auth:nacos", request.getPluginId());
    }
    
    @Test
    void setPluginIdNullTest() {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        
        request.setPluginId("trace:test");
        request.setPluginId(null);
        
        assertNull(request.getPluginId());
    }
}
