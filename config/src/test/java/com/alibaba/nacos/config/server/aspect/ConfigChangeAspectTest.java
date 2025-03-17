/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.aspect;

import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.config.server.configuration.ConfigChangeConfigs;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.plugin.config.ConfigChangePluginManager;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeExecuteTypes;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;
import com.alibaba.nacos.plugin.config.spi.ConfigChangePluginService;
import com.alibaba.nacos.sys.utils.PropertiesUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.servlet.http.HttpServletRequest;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ConfigChangeAspectTest {
    
    ConfigChangeAspect configChangeAspect;
    
    ConfigChangeConfigs configChangeConfigs;
    
    @Mock
    ConfigChangePluginService configChangePluginService;
    
    MockedStatic<PropertiesUtil> propertiesStatic;
    
    MockedStatic<RequestUtil> requestUtilMockedStatic;
    
    @Mock
    private ProceedingJoinPoint pjp;
    
    @Mock
    private ConfigForm configForm;
    
    @Mock
    private ConfigRequestInfo configRequestInfo;
    
    @BeforeEach
    void before() {
        //mock config change service enabled.
        propertiesStatic = Mockito.mockStatic(PropertiesUtil.class);
        requestUtilMockedStatic = Mockito.mockStatic(RequestUtil.class);
        Properties properties = new Properties();
        properties.put("mockedConfigChangeService.enabled", "true");
        propertiesStatic.when(
                () -> PropertiesUtil.getPropertiesWithPrefix(any(), eq(ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX)))
                .thenReturn(properties);
        requestUtilMockedStatic.when(() -> RequestUtil.getSrcUserName(any(HttpServletRequest.class))).thenReturn("mockedUser");
        Mockito.when(configChangePluginService.getServiceType()).thenReturn("mockedConfigChangeService");
        Mockito.when(configChangePluginService.pointcutMethodNames()).thenReturn(ConfigChangePointCutTypes.values());
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        
        ConfigChangePluginManager.join(configChangePluginService);
        
        configChangeConfigs = new ConfigChangeConfigs();
        configChangeAspect = new ConfigChangeAspect(configChangeConfigs);
    }
    
    @AfterEach
    void after() {
        propertiesStatic.close();
        requestUtilMockedStatic.close();
    }
    
    @Test
    void testPublishOrUpdateConfigAround() throws Throwable {
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        
        when(pjp.getArgs()).thenReturn(new Object[]{configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn("dataId");
        when(configForm.getGroup()).thenReturn("group");
        when(configForm.getNamespaceId()).thenReturn("namespaceId");
        when(configForm.getContent()).thenReturn("content");
        when(configRequestInfo.getSrcIp()).thenReturn("127.0.0.1");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(pjp.proceed(any())).thenReturn("Success");
        
        Object o = configChangeAspect.publishOrUpdateConfigAround(pjp);
        Thread.sleep(20L);
        
        // expect service executed.
        Mockito.verify(configChangePluginService, Mockito.times(1))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        //expect join point processed success.
        assertEquals("Success", o);
    }
    
    @Test
    void testRemoveConfigByIdAround() throws Throwable {
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        
        String dataId = "dataId1";
        String group = "group1";
        String namespaceId = "namespaceId1";
        String tag = "tag1";
        String clientIp = "127.0.0.1";
        String srcUser = "mockedUser";
        String srcType = "http";
        
        when(pjp.getArgs()).thenReturn(new Object[]{dataId, group, namespaceId, tag, clientIp, srcUser, srcType});
        Mockito.when(pjp.proceed(any())).thenReturn("mock success return");
        Object o = configChangeAspect.removeConfigByIdAround(pjp);
        Thread.sleep(20L);
        
        // expect service executed.
        Mockito.verify(configChangePluginService, Mockito.times(1))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        //expect join point processed success.
        assertEquals("mock success return", o);
    }
    
    @Test
    void testDisEnablePluginService() throws Throwable {
        Properties properties = new Properties();
        properties.put("mockedConfigChangeService.enabled", "false");
        String dataId = "dataId1";
        String group = "group1";
        String namespaceId = "namespaceId1";
        String tag = "tag1";
        String clientIp = "127.0.0.1";
        String srcUser = "mockedUser";
        String srcType = "http";
        
        when(pjp.getArgs()).thenReturn(new Object[]{dataId, group, namespaceId, tag, clientIp, srcUser, srcType});
        propertiesStatic.when(
                () -> PropertiesUtil.getPropertiesWithPrefix(any(), eq(ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX)))
                .thenReturn(properties);
        configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        assertFalse(Boolean.parseBoolean(configChangeConfigs.getPluginProperties("mockedConfigChangeService").getProperty("enabled")));
        
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        Mockito.when(configChangePluginService.getServiceType()).thenReturn("mockedConfigChangeService");
        ConfigPublishResponse configPublishResponse = ConfigPublishResponse.buildSuccessResponse();
        Mockito.when(pjp.proceed()).thenReturn(configPublishResponse);
        //execute
        Object o = configChangeAspect.removeConfigByIdAround(pjp);
        //expect
        Mockito.verify(configChangePluginService, Mockito.times(0))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        assertEquals(configPublishResponse, o);
    }
    
}
