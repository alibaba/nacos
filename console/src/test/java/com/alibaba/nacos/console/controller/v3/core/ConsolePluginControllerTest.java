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

package com.alibaba.nacos.console.controller.v3.core;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.core.PluginProxy;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConsolePluginControllerTest {
    
    @Mock
    private PluginProxy pluginProxy;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConsolePluginController(pluginProxy))
            .setControllerAdvice(new NacosApiExceptionHandler())
            .build();
    }
    
    @Test
    void testGetPluginList() throws Exception {
        PluginInfoVO vo = new PluginInfoVO();
        vo.setPluginName("test-plugin");
        vo.setPluginType("auth");
        when(pluginProxy.listPlugins(eq("auth"))).thenReturn(List.of(vo));
        
        MockHttpServletResponse response = mockMvc.perform(
            get("/v3/console/plugin/list").param("pluginType", "auth"))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<List<PluginInfoVO>> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(1, result.getData().size());
        assertEquals("test-plugin", result.getData().get(0).getPluginName());
    }
    
    @Test
    void testGetPluginListWithoutType() throws Exception {
        when(pluginProxy.listPlugins(eq(null))).thenReturn(Collections.emptyList());
        
        MockHttpServletResponse response =
            mockMvc.perform(get("/v3/console/plugin/list")).andExpect(status().isOk()).andReturn()
                .getResponse();
        
        Result<List<PluginInfoVO>> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(0, result.getData().size());
    }
    
    @Test
    void testGetPluginDetail() throws Exception {
        PluginDetailVO detail = new PluginDetailVO();
        detail.setPluginName("test-plugin");
        detail.setPluginType("auth");
        when(pluginProxy.getPluginDetail("auth", "test-plugin")).thenReturn(detail);
        
        MockHttpServletResponse response = mockMvc.perform(
            get("/v3/console/plugin").param("pluginType", "auth")
                .param("pluginName", "test-plugin"))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<PluginDetailVO> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertNotNull(result.getData());
        assertEquals("test-plugin", result.getData().getPluginName());
    }
    
    @Test
    void testUpdatePluginStatus() throws Exception {
        doNothing().when(pluginProxy)
            .updatePluginStatus(anyString(), anyString(), anyBoolean(), anyBoolean());
        
        MockHttpServletResponse response = mockMvc.perform(
            put("/v3/console/plugin/status").param("pluginType", "auth")
                .param("pluginName", "test-plugin").param("enabled", "true")
                .param("localOnly", "false"))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("Plugin status updated successfully", result.getData());
        verify(pluginProxy).updatePluginStatus("auth", "test-plugin", true, false);
    }
    
    @Test
    void testUpdatePluginConfig() throws Exception {
        doNothing().when(pluginProxy)
            .updatePluginConfig(anyString(), anyString(), any(), anyBoolean());
        
        MockHttpServletResponse response = mockMvc.perform(
            put("/v3/console/plugin/config").param("pluginType", "auth")
                .param("pluginName", "test-plugin").param("config[key1]", "val1")
                .param("localOnly", "false"))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<String> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals("Plugin configuration updated successfully", result.getData());
    }
    
    @Test
    void testUpdatePluginConfigMissingType() throws Exception {
        mockMvc.perform(put("/v3/console/plugin/config").param("pluginName", "test-plugin")
            .param("config[key1]", "val1"))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void testUpdatePluginConfigMissingName() throws Exception {
        mockMvc.perform(put("/v3/console/plugin/config").param("pluginType", "auth")
            .param("config[key1]", "val1"))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void testGetPluginAvailability() throws Exception {
        Map<String, Boolean> availability = Map.of("node1", true, "node2", false);
        when(pluginProxy.getPluginAvailability("auth", "test-plugin")).thenReturn(availability);
        
        MockHttpServletResponse response = mockMvc.perform(
            get("/v3/console/plugin/availability").param("pluginType", "auth")
                .param("pluginName", "test-plugin"))
            .andExpect(status().isOk()).andReturn().getResponse();
        
        Result<Map<String, Boolean>> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(2, result.getData().size());
    }
}
