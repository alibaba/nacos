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

package com.alibaba.nacos.copilot.config;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerFactory;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for CopilotConfigStorage.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class CopilotConfigStorageTest {
    
    private static final String DATA_ID = "copilot-config.json";
    
    private static final String GROUP = "nacos-copilot";
    
    private static final String NAMESPACE = "public";
    
    private static final String SERVER_ADDR = "127.0.0.1:8848";
    
    private static final String CONTEXT_PATH = "/nacos";
    
    @Mock
    private ConfigMaintainerService configMaintainerService;
    
    private CopilotConfigStorage storage;
    
    @BeforeEach
    void setUp() {
        storage = new CopilotConfigStorage();
        ReflectionTestUtils.setField(storage, "configNamespace", NAMESPACE);
        ReflectionTestUtils.setField(storage, "serverAddr", SERVER_ADDR);
        ReflectionTestUtils.setField(storage, "contextPath", "");
        ReflectionTestUtils.setField(storage, "configMaintainerService", configMaintainerService);
    }
    
    @Test
    void testInitUsesEnvironmentContextPath() throws Exception {
        try (MockedStatic<EnvUtil> envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
            MockedStatic<ConfigMaintainerFactory> factoryMockedStatic =
                mockStatic(ConfigMaintainerFactory.class)) {
            ArgumentCaptor<Properties> captor = ArgumentCaptor.forClass(Properties.class);
            envUtilMockedStatic.when(EnvUtil::getContextPath).thenReturn(CONTEXT_PATH);
            factoryMockedStatic
                .when(() -> ConfigMaintainerFactory.createConfigMaintainerService(captor.capture()))
                .thenReturn(configMaintainerService);
            
            storage.init();
            
            Properties properties = captor.getValue();
            assertEquals(SERVER_ADDR, properties.getProperty(PropertyKeyConst.SERVER_ADDR));
            assertEquals(CONTEXT_PATH, properties.getProperty(PropertyKeyConst.CONTEXT_PATH));
            assertTrue(storage.isAvailable());
        }
    }
    
    @Test
    void testInitUsesExplicitContextPath() throws Exception {
        ReflectionTestUtils.setField(storage, "contextPath", "/custom");
        try (MockedStatic<ConfigMaintainerFactory> factoryMockedStatic =
            mockStatic(ConfigMaintainerFactory.class)) {
            ArgumentCaptor<Properties> captor = ArgumentCaptor.forClass(Properties.class);
            factoryMockedStatic
                .when(() -> ConfigMaintainerFactory.createConfigMaintainerService(captor.capture()))
                .thenReturn(configMaintainerService);
            
            storage.init();
            
            Properties properties = captor.getValue();
            assertEquals(SERVER_ADDR, properties.getProperty(PropertyKeyConst.SERVER_ADDR));
            assertEquals("/custom", properties.getProperty(PropertyKeyConst.CONTEXT_PATH));
        }
    }
    
    @Test
    void testSaveConfigReturnsTrueWhenPublishSucceeds() throws Exception {
        CopilotProperties config = newConfig();
        String content = JacksonUtils.toJson(config);
        whenPublishConfig(content, true);
        
        assertTrue(storage.saveConfig(config));
        verify(configMaintainerService, never()).getConfig(DATA_ID, GROUP, NAMESPACE);
    }
    
    @Test
    void testSaveConfigReturnsTrueWhenPublishedContentAlreadyExists() throws Exception {
        CopilotProperties config = newConfig();
        String content = JacksonUtils.toJson(config);
        whenPublishConfig(content, false);
        when(configMaintainerService.getConfig(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(content));
        
        assertTrue(storage.saveConfig(config));
    }
    
    @Test
    void testSaveConfigReturnsFalseWhenPublishedContentDoesNotMatch() throws Exception {
        CopilotProperties config = newConfig();
        String content = JacksonUtils.toJson(config);
        whenPublishConfig(content, false);
        when(configMaintainerService.getConfig(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail("{}"));
        
        assertFalse(storage.saveConfig(config));
    }
    
    @Test
    void testSaveConfigReturnsTrueWhenPublishThrowsButTargetContentExists() throws Exception {
        CopilotProperties config = newConfig();
        String content = JacksonUtils.toJson(config);
        when(configMaintainerService.publishConfig(eq(DATA_ID), eq(GROUP), eq(NAMESPACE),
            eq(content), eq("nacos-copilot"), eq("system"), eq(null),
            eq("Copilot configuration"), eq("json"))).thenThrow(
                new NacosException(NacosException.SERVER_ERROR, "failed"));
        when(configMaintainerService.getConfig(DATA_ID, GROUP, NAMESPACE))
            .thenReturn(configDetail(content));
        
        assertTrue(storage.saveConfig(config));
    }
    
    @Test
    void testSaveConfigReturnsFalseWhenPublishThrowsException() throws Exception {
        CopilotProperties config = newConfig();
        String content = JacksonUtils.toJson(config);
        when(configMaintainerService.publishConfig(eq(DATA_ID), eq(GROUP), eq(NAMESPACE),
            eq(content), eq("nacos-copilot"), eq("system"), eq(null),
            eq("Copilot configuration"), eq("json"))).thenThrow(
                new NacosException(NacosException.SERVER_ERROR, "failed"));
        
        assertFalse(storage.saveConfig(config));
    }
    
    private void whenPublishConfig(String content, boolean result) throws NacosException {
        when(configMaintainerService.publishConfig(eq(DATA_ID), eq(GROUP), eq(NAMESPACE),
            eq(content), eq("nacos-copilot"), eq("system"), eq(null),
            eq("Copilot configuration"), eq("json"))).thenReturn(result);
    }
    
    private CopilotProperties newConfig() {
        CopilotProperties result = new CopilotProperties();
        result.setApiKey("openapi-it-key");
        result.setModel("openapi-it-model");
        result.setStudioUrl("http://127.0.0.1/studio");
        result.setStudioProject("openapi-it-project");
        return result;
    }
    
    private ConfigDetailInfo configDetail(String content) {
        ConfigDetailInfo result = new ConfigDetailInfo();
        result.setContent(content);
        return result;
    }
}
