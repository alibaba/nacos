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

package com.alibaba.nacos.core.controller.v3;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.core.plugin.PluginManager;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PluginControllerV3} unit test.
 *
 * @author WangzJi
 */
@ExtendWith(MockitoExtension.class)
class PluginControllerV3Test {
    
    @Mock
    private PluginManager unifiedPluginManager;
    
    @InjectMocks
    private PluginControllerV3 pluginControllerV3;
    
    private static PluginInfo createPluginInfo(String id, PluginType type, String name) {
        PluginInfo info = new PluginInfo();
        info.setPluginId(id);
        info.setPluginType(type);
        info.setPluginName(name);
        info.setEnabled(true);
        info.setCritical(false);
        info.setConfigurable(true);
        return info;
    }
    
    @Test
    void testGetPluginListWithoutFilter() {
        PluginInfo info = createPluginInfo("auth:nacos", PluginType.AUTH, "nacos");
        when(unifiedPluginManager.listAllPlugins()).thenReturn(Collections.singletonList(info));
        
        Result<List<PluginInfoVO>> result = pluginControllerV3.getPluginList(null);
        
        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("auth:nacos", result.getData().get(0).getPluginId());
        assertEquals("auth", result.getData().get(0).getPluginType());
        assertEquals("nacos", result.getData().get(0).getPluginName());
        assertTrue(result.getData().get(0).getExclusive());
    }
    
    @Test
    void testGetPluginListWithPluginTypeFilter() {
        PluginInfo info = createPluginInfo("auth:nacos", PluginType.AUTH, "nacos");
        when(unifiedPluginManager.listAllPlugins()).thenReturn(Collections.singletonList(info));
        
        Result<List<PluginInfoVO>> result = pluginControllerV3.getPluginList("auth");
        
        assertNotNull(result);
        assertEquals(1, result.getData().size());
        assertEquals("auth", result.getData().get(0).getPluginType());
    }
    
    @Test
    void testGetPluginListWithNonMatchingFilter() {
        PluginInfo info = createPluginInfo("auth:nacos", PluginType.AUTH, "nacos");
        when(unifiedPluginManager.listAllPlugins()).thenReturn(Collections.singletonList(info));
        
        Result<List<PluginInfoVO>> result = pluginControllerV3.getPluginList("config-change");
        
        assertNotNull(result);
        assertTrue(result.getData().isEmpty());
    }
    
    @Test
    void testGetPluginDetail() throws NacosApiException {
        PluginInfo info = createPluginInfo("auth:nacos", PluginType.AUTH, "nacos");
        info.setConfig(Collections.singletonMap("key", "value"));
        when(unifiedPluginManager.getPlugin("auth:nacos")).thenReturn(Optional.of(info));
        
        Result<PluginDetailVO> result = pluginControllerV3.getPluginDetail("auth", "nacos");
        
        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("auth:nacos", result.getData().getPluginId());
        assertEquals("auth", result.getData().getPluginType());
        assertEquals("nacos", result.getData().getPluginName());
        assertNotNull(result.getData().getConfig());
    }
    
    @Test
    void testGetPluginDetailNotFound() {
        when(unifiedPluginManager.getPlugin("auth:missing")).thenReturn(Optional.empty());
        
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> pluginControllerV3.getPluginDetail("auth", "missing"));
        
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("Plugin not found"));
    }
    
    @Test
    void testUpdatePluginStatus() throws NacosApiException {
        Result<String> result = pluginControllerV3.updatePluginStatus("auth", "nacos", false, true);
        
        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(unifiedPluginManager).setPluginEnabled("auth:nacos", false, true);
    }
    
    @Test
    void testUpdatePluginStatusBlankTypeThrows() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> pluginControllerV3.updatePluginStatus("", "nacos", true, false));
        
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getErrCode());
    }
    
    @Test
    void testUpdatePluginStatusBlankNameThrows() {
        assertThrows(NacosApiException.class,
            () -> pluginControllerV3.updatePluginStatus("auth", "  ", true, false));
    }
    
    @Test
    void testUpdatePluginConfig() throws NacosApiException {
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        String configJson = "{\"key\":\"value\"}";
        
        Result<String> result =
            pluginControllerV3.updatePluginConfig("auth", "nacos", configJson, false);
        
        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(unifiedPluginManager).updatePluginConfig(eq("auth:nacos"), eq(config), eq(false));
    }
    
    @Test
    void testUpdatePluginConfigNullConfigThrows() {
        assertThrows(Exception.class,
            () -> pluginControllerV3.updatePluginConfig("auth", "nacos", "null", false));
    }
    
    @Test
    void testGetPluginListDatasourceDialectExclusive() {
        PluginInfo info =
            createPluginInfo("datasource-dialect:mysql", PluginType.DATASOURCE_DIALECT, "mysql");
        when(unifiedPluginManager.listAllPlugins()).thenReturn(Collections.singletonList(info));
        
        Result<List<PluginInfoVO>> result = pluginControllerV3.getPluginList(null);
        
        assertTrue(result.getData().get(0).getExclusive());
    }
}
