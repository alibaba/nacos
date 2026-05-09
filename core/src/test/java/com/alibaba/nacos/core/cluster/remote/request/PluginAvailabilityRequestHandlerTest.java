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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.core.cluster.remote.response.PluginAvailabilityResponse;
import com.alibaba.nacos.core.plugin.PluginManager;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link PluginAvailabilityRequestHandler} unit test.
 *
 * @author WangzJi
 */
@ExtendWith(MockitoExtension.class)
class PluginAvailabilityRequestHandlerTest {
    
    @Mock
    private PluginManager pluginManager;
    
    private PluginAvailabilityRequestHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new PluginAvailabilityRequestHandler(pluginManager);
    }
    
    @Test
    void handleQueryAllTest() throws NacosException {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        request.setQueryAll(true);
        
        PluginInfo plugin1 = createPluginInfo("trace:otel", PluginType.TRACE, "otel", true);
        PluginInfo plugin2 = createPluginInfo("auth:nacos", PluginType.AUTH, "nacos", false);
        
        when(pluginManager.listAllPlugins()).thenReturn(Arrays.asList(plugin1, plugin2));
        
        PluginAvailabilityResponse response = handler.handle(request, null);
        
        assertNotNull(response);
        assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        
        Map<String, Boolean> availabilityMap = response.getPluginAvailabilityMap();
        assertNotNull(availabilityMap);
        assertEquals(2, availabilityMap.size());
        assertTrue(availabilityMap.get("trace:otel"));
        assertFalse(availabilityMap.get("auth:nacos"));
    }
    
    @Test
    void handleSinglePluginAvailableTest() throws NacosException {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        request.setQueryAll(false);
        request.setPluginId("trace:otel");
        
        when(pluginManager.isPluginAvailable("trace:otel")).thenReturn(true);
        
        PluginAvailabilityResponse response = handler.handle(request, null);
        
        assertNotNull(response);
        assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        assertEquals("trace:otel", response.getPluginId());
        assertTrue(response.isAvailable());
    }
    
    @Test
    void handleSinglePluginNotAvailableTest() throws NacosException {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        request.setQueryAll(false);
        request.setPluginId("auth:custom");
        
        when(pluginManager.isPluginAvailable("auth:custom")).thenReturn(false);
        
        PluginAvailabilityResponse response = handler.handle(request, null);
        
        assertNotNull(response);
        assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        assertEquals("auth:custom", response.getPluginId());
        assertFalse(response.isAvailable());
    }
    
    @Test
    void handleNullPluginIdTest() throws NacosException {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        request.setQueryAll(false);
        request.setPluginId(null);
        
        PluginAvailabilityResponse response = handler.handle(request, null);
        
        assertNotNull(response);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals("Either queryAll must be true or pluginId must be provided",
            response.getMessage());
    }
    
    private PluginInfo createPluginInfo(String pluginId, PluginType pluginType, String pluginName,
        boolean enabled) {
        PluginInfo info = new PluginInfo();
        info.setPluginId(pluginId);
        info.setPluginType(pluginType);
        info.setPluginName(pluginName);
        info.setEnabled(enabled);
        return info;
    }
}
