/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.proxy.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.core.PluginHandler;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginProxyTest {
    
    @Mock
    private PluginHandler pluginHandler;
    
    private PluginProxy pluginProxy;
    
    @BeforeEach
    void setUp() {
        pluginProxy = new PluginProxy(pluginHandler);
    }
    
    @Test
    void testListPlugins() throws NacosException {
        List<PluginInfoVO> plugins = List.of(new PluginInfoVO());
        when(pluginHandler.listPlugins("auth")).thenReturn(plugins);
        
        List<PluginInfoVO> result = pluginProxy.listPlugins("auth");
        
        assertEquals(1, result.size());
        verify(pluginHandler).listPlugins("auth");
    }
    
    @Test
    void testGetPluginDetail() throws NacosException {
        PluginDetailVO detail = new PluginDetailVO();
        when(pluginHandler.getPluginDetail("auth", "default")).thenReturn(detail);
        
        PluginDetailVO result = pluginProxy.getPluginDetail("auth", "default");
        
        assertNotNull(result);
        verify(pluginHandler).getPluginDetail("auth", "default");
    }
    
    @Test
    void testUpdatePluginStatus() throws NacosException {
        doNothing().when(pluginHandler).updatePluginStatus("auth", "default",
            true, false);
        
        pluginProxy.updatePluginStatus("auth", "default", true, false);
        
        verify(pluginHandler).updatePluginStatus("auth", "default", true, false);
    }
    
    @Test
    void testUpdatePluginConfig() throws NacosException {
        Map<String, String> config = Map.of("key", "value");
        doNothing().when(pluginHandler).updatePluginConfig("auth", "default",
            config, true);
        
        pluginProxy.updatePluginConfig("auth", "default", config, true);
        
        verify(pluginHandler).updatePluginConfig("auth", "default", config, true);
    }
    
    @Test
    void testGetPluginAvailability() throws NacosException {
        Map<String, Boolean> availability = Map.of("node1", true, "node2", false);
        when(pluginHandler.getPluginAvailability("auth", "default"))
            .thenReturn(availability);
        
        Map<String, Boolean> result = pluginProxy.getPluginAvailability("auth", "default");
        
        assertEquals(2, result.size());
        verify(pluginHandler).getPluginAvailability("auth", "default");
    }
}
