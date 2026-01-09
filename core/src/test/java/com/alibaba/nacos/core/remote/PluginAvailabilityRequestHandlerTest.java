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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.cluster.remote.request.PluginAvailabilityRequest;
import com.alibaba.nacos.core.cluster.remote.response.PluginAvailabilityResponse;
import com.alibaba.nacos.core.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
        handler = new PluginAvailabilityRequestHandler();
        ReflectionTestUtils.setField(handler, "pluginManager", pluginManager);
    }

    @Test
    void handlePluginAvailableTest() throws NacosException {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        request.setPluginId("trace:otel");

        when(pluginManager.isPluginAvailable("trace:otel")).thenReturn(true);

        PluginAvailabilityResponse response = handler.handle(request, null);

        assertNotNull(response);
        assertEquals("trace:otel", response.getPluginId());
        assertTrue(response.isAvailable());
    }

    @Test
    void handlePluginNotAvailableTest() throws NacosException {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        request.setPluginId("auth:custom");

        when(pluginManager.isPluginAvailable("auth:custom")).thenReturn(false);

        PluginAvailabilityResponse response = handler.handle(request, null);

        assertNotNull(response);
        assertEquals("auth:custom", response.getPluginId());
        assertFalse(response.isAvailable());
    }

    @Test
    void handleNullPluginIdTest() throws NacosException {
        PluginAvailabilityRequest request = new PluginAvailabilityRequest();
        request.setPluginId(null);

        when(pluginManager.isPluginAvailable(null)).thenReturn(false);

        PluginAvailabilityResponse response = handler.handle(request, null);

        assertNotNull(response);
        assertFalse(response.isAvailable());
    }
}
