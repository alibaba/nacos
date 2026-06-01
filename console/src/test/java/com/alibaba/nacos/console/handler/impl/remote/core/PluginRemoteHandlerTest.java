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

package com.alibaba.nacos.console.handler.impl.remote.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.console.handler.impl.remote.AbstractRemoteHandlerTest;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    PluginRemoteHandler pluginRemoteHandler;
    
    @BeforeEach
    void setUp() {
        super.setUpWithNaming();
        pluginRemoteHandler = new PluginRemoteHandler(clientHolder);
    }
    
    @Test
    void testListPluginsTest() throws NacosException {
        List<Map<String, Object>> mockList = new ArrayList<>();
        Map<String, Object> pluginData = createMockPluginData();
        mockList.add(pluginData);
        
        when(namingMaintainerService.listPlugins(null)).thenReturn(mockList);
        
        List<PluginInfoVO> result = pluginRemoteHandler.listPlugins(null);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("auth:test", result.get(0).getPluginId());
        assertEquals("auth", result.get(0).getPluginType());
        assertEquals("test", result.get(0).getPluginName());
        assertTrue(result.get(0).getEnabled());
    }
    
    @Test
    void testListPluginsWithTypeFilterTest() throws NacosException {
        List<Map<String, Object>> mockList = new ArrayList<>();
        Map<String, Object> pluginData = createMockPluginData();
        mockList.add(pluginData);
        
        when(namingMaintainerService.listPlugins("auth")).thenReturn(mockList);
        
        List<PluginInfoVO> result = pluginRemoteHandler.listPlugins("auth");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(namingMaintainerService).listPlugins("auth");
    }
    
    @Test
    void testGetPluginDetailTest() throws NacosException {
        Map<String, Object> mockDetail = createMockPluginDetailData();
        
        when(namingMaintainerService.getPluginDetail("auth", "test")).thenReturn(mockDetail);
        
        PluginDetailVO result = pluginRemoteHandler.getPluginDetail("auth", "test");
        
        assertNotNull(result);
        assertEquals("auth:test", result.getPluginId());
        assertEquals("auth", result.getPluginType());
        assertEquals("test", result.getPluginName());
        assertTrue(result.getConfigurable());
        assertNotNull(result.getConfig());
        assertEquals("value1", result.getConfig().get("key1"));
    }
    
    @Test
    void testUpdatePluginStatusTest() throws NacosException {
        doNothing().when(namingMaintainerService).updatePluginStatus("auth", "test", false, false);
        
        pluginRemoteHandler.updatePluginStatus("auth", "test", false, false);
        
        verify(namingMaintainerService).updatePluginStatus("auth", "test", false, false);
    }
    
    @Test
    void testUpdatePluginConfigTest() throws NacosException {
        Map<String, String> config = new HashMap<>();
        config.put("key1", "value1");
        
        doNothing().when(namingMaintainerService).updatePluginConfig(eq("auth"), eq("test"), any(),
            eq(false));
        
        pluginRemoteHandler.updatePluginConfig("auth", "test", config, false);
        
        verify(namingMaintainerService).updatePluginConfig("auth", "test", config, false);
    }
    
    @Test
    void testGetPluginAvailabilityTest() throws NacosException {
        Map<String, Boolean> mockAvailability = new HashMap<>();
        mockAvailability.put("127.0.0.1:8848", true);
        mockAvailability.put("127.0.0.2:8848", false);
        
        when(namingMaintainerService.getPluginAvailability("auth", "test"))
            .thenReturn(mockAvailability);
        
        Map<String, Boolean> result = pluginRemoteHandler.getPluginAvailability("auth", "test");
        
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.get("127.0.0.1:8848"));
        assertFalse(result.get("127.0.0.2:8848"));
    }
    
    @Test
    void testConvertPluginInfoVOWithNodeCountTest() throws NacosException {
        List<Map<String, Object>> mockList = new ArrayList<>();
        Map<String, Object> pluginData = createMockPluginData();
        pluginData.put("totalNodeCount", 3);
        pluginData.put("availableNodeCount", 2);
        mockList.add(pluginData);
        
        when(namingMaintainerService.listPlugins(null)).thenReturn(mockList);
        
        List<PluginInfoVO> result = pluginRemoteHandler.listPlugins(null);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getTotalNodeCount());
        assertEquals(2, result.get(0).getAvailableNodeCount());
    }
    
    @Test
    void testConvertPluginDetailWithConfigDefinitionsTest() throws NacosException {
        Map<String, Object> mockDetail = createMockPluginDetailData();
        
        List<Map<String, Object>> configDefinitions = new ArrayList<>();
        Map<String, Object> configDef = new HashMap<>();
        configDef.put("key", "timeout");
        configDef.put("name", "Timeout");
        configDef.put("description", "Request timeout in ms");
        configDef.put("defaultValue", "5000");
        configDef.put("type", "NUMBER");
        configDef.put("required", true);
        configDefinitions.add(configDef);
        mockDetail.put("configDefinitions", configDefinitions);
        
        when(namingMaintainerService.getPluginDetail("auth", "test")).thenReturn(mockDetail);
        
        PluginDetailVO result = pluginRemoteHandler.getPluginDetail("auth", "test");
        
        assertNotNull(result);
        assertNotNull(result.getConfigDefinitions());
        assertEquals(1, result.getConfigDefinitions().size());
        assertEquals("timeout", result.getConfigDefinitions().get(0).getKey());
        assertEquals(ConfigItemType.NUMBER, result.getConfigDefinitions().get(0).getType());
    }
    
    @Test
    void testConvertUnknownEnumTypeTest() throws NacosException {
        Map<String, Object> mockDetail = createMockPluginDetailData();
        
        List<Map<String, Object>> configDefinitions = new ArrayList<>();
        Map<String, Object> configDef = new HashMap<>();
        configDef.put("key", "unknown");
        configDef.put("name", "Unknown");
        configDef.put("type", "UNKNOWN_TYPE");
        configDefinitions.add(configDef);
        mockDetail.put("configDefinitions", configDefinitions);
        
        when(namingMaintainerService.getPluginDetail("auth", "test")).thenReturn(mockDetail);
        
        PluginDetailVO result = pluginRemoteHandler.getPluginDetail("auth", "test");
        
        assertNotNull(result);
        assertNotNull(result.getConfigDefinitions());
        assertEquals(1, result.getConfigDefinitions().size());
        assertEquals(ConfigItemType.STRING, result.getConfigDefinitions().get(0).getType());
    }
    
    private Map<String, Object> createMockPluginData() {
        Map<String, Object> data = new HashMap<>();
        data.put("pluginId", "auth:test");
        data.put("pluginType", "auth");
        data.put("pluginName", "test");
        data.put("enabled", true);
        data.put("critical", false);
        data.put("configurable", true);
        data.put("exclusive", true);
        return data;
    }
    
    private Map<String, Object> createMockPluginDetailData() {
        Map<String, Object> data = createMockPluginData();
        Map<String, String> config = new HashMap<>();
        config.put("key1", "value1");
        data.put("config", config);
        return data;
    }
}
