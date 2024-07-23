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

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.config.server.configuration.ConfigChangeConfigs;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
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
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(SpringExtension.class)
class ConfigChangeAspectTest {
    
    ConfigChangeAspect configChangeAspect;
    
    ConfigChangeConfigs configChangeConfigs;
    
    @Mock
    ConfigChangePluginService configChangePluginService;
    
    MockedStatic<PropertiesUtil> propertiesStatic;
    
    MockedStatic<RequestUtil> requestUtilMockedStatic;
    
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
    void testImportConfigAround() throws Throwable {
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        ProceedingJoinPoint proceedingJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        String srcUser = "user12324";
        String namespace = "tenant234";
        SameConfigPolicy policy = SameConfigPolicy.ABORT;
        MultipartFile file = Mockito.mock(MultipartFile.class);
        Mockito.when(proceedingJoinPoint.proceed(any())).thenReturn("mock success return");
        Object o = configChangeAspect.importConfigAround(proceedingJoinPoint, request, srcUser, namespace, policy, file);
        Thread.sleep(20L);
        
        // expect service executed.
        Mockito.verify(configChangePluginService, Mockito.times(1))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        //expect join point processed success.
        assertEquals("mock success return", o);
    }
    
    @Test
    void testPublishOrUpdateConfigAround() throws Throwable {
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        ProceedingJoinPoint proceedingJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        
        String srcUser = "user12324";
        String dataId = "d1";
        String group = "g1";
        String tenant = "t1";
        Mockito.when(proceedingJoinPoint.proceed(any())).thenReturn("mock success return");
        Object o = configChangeAspect.publishOrUpdateConfigAround(proceedingJoinPoint, request, response, dataId, group, tenant, "c1", null,
                null, srcUser, null, null, null, null, null);
        Thread.sleep(20L);
        
        // expect service executed.
        Mockito.verify(configChangePluginService, Mockito.times(1))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        //expect join point processed success.
        assertEquals("mock success return", o);
    }
    
    @Test
    void testRemoveConfigByIdAround() throws Throwable {
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        ProceedingJoinPoint proceedingJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        
        String dataId = "d1";
        String group = "g1";
        String tenant = "t1";
        Mockito.when(proceedingJoinPoint.proceed(any())).thenReturn("mock success return");
        Object o = configChangeAspect.removeConfigByIdAround(proceedingJoinPoint, request, response, dataId, group, tenant);
        Thread.sleep(20L);
        
        // expect service executed.
        Mockito.verify(configChangePluginService, Mockito.times(1))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        //expect join point processed success.
        assertEquals("mock success return", o);
    }
    
    @Test
    void testRemoveConfigByIdsAround() throws Throwable {
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        ProceedingJoinPoint proceedingJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        
        Mockito.when(proceedingJoinPoint.proceed(any())).thenReturn("mock success return");
        Object o = configChangeAspect.removeConfigByIdsAround(proceedingJoinPoint, request, Arrays.asList(1L, 2L));
        Thread.sleep(20L);
        // expect service executed.
        Mockito.verify(configChangePluginService, Mockito.times(1))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        //expect join point processed success.
        assertEquals("mock success return", o);
    }
    
    @Test
    void testPublishConfigAroundRpc() throws Throwable {
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        ProceedingJoinPoint proceedingJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        ConfigPublishRequest request = new ConfigPublishRequest();
        RequestMeta requestMeta = new RequestMeta();
        ConfigPublishResponse configPublishResponse = ConfigPublishResponse.buildSuccessResponse();
        Mockito.when(proceedingJoinPoint.proceed(any())).thenReturn(configPublishResponse);
        //execute
        Object o = configChangeAspect.publishConfigAroundRpc(proceedingJoinPoint, request, requestMeta);
        //expect
        Mockito.verify(configChangePluginService, Mockito.times(1))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        assertEquals(configPublishResponse, o);
    }
    
    @Test
    void testPublishConfigAroundRpcException() throws Throwable {
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        ProceedingJoinPoint proceedingJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        ConfigPublishRequest request = new ConfigPublishRequest();
        RequestMeta requestMeta = new RequestMeta();
        
        Mockito.when(proceedingJoinPoint.proceed(any())).thenThrow(new NacosRuntimeException(503));
        //execute
        Object o = configChangeAspect.publishConfigAroundRpc(proceedingJoinPoint, request, requestMeta);
        //expect
        Mockito.verify(configChangePluginService, Mockito.times(1))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        
        assertTrue(((ConfigPublishResponse) o).getMessage().contains("config change join point fail"));
    }
    
    @Test
    void testRemoveConfigAroundRpc() throws Throwable {
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        ProceedingJoinPoint proceedingJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        ConfigRemoveRequest request = new ConfigRemoveRequest();
        RequestMeta requestMeta = new RequestMeta();
        ConfigPublishResponse configPublishResponse = ConfigPublishResponse.buildSuccessResponse();
        Mockito.when(proceedingJoinPoint.proceed(any())).thenReturn(configPublishResponse);
        //execute
        Object o = configChangeAspect.removeConfigAroundRpc(proceedingJoinPoint, request, requestMeta);
        //expect
        Mockito.verify(configChangePluginService, Mockito.times(1))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        assertEquals(configPublishResponse, o);
    }
    
    @Test
    void testRemoveConfigAroundRpcException() throws Throwable {
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        ProceedingJoinPoint proceedingJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        ConfigRemoveRequest request = new ConfigRemoveRequest();
        RequestMeta requestMeta = new RequestMeta();
        
        Mockito.when(proceedingJoinPoint.proceed(any())).thenThrow(new NacosRuntimeException(503));
        //execute
        Object o = configChangeAspect.removeConfigAroundRpc(proceedingJoinPoint, request, requestMeta);
        //expect
        Mockito.verify(configChangePluginService, Mockito.times(1))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        
        assertTrue(((ConfigRemoveResponse) o).getMessage().contains("config change join point fail"));
    }
    
    @Test
    void testDisEnablePluginService() throws Throwable {
        Properties properties = new Properties();
        properties.put("mockedConfigChangeService.enabled", "false");
        propertiesStatic.when(
                () -> PropertiesUtil.getPropertiesWithPrefix(any(), eq(ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX)))
                .thenReturn(properties);
        configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        assertFalse(Boolean.parseBoolean(configChangeConfigs.getPluginProperties("mockedConfigChangeService").getProperty("enabled")));
        
        Mockito.when(configChangePluginService.executeType()).thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        Mockito.when(configChangePluginService.getServiceType()).thenReturn("mockedConfigChangeService");
        ProceedingJoinPoint proceedingJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        ConfigRemoveRequest request = new ConfigRemoveRequest();
        RequestMeta requestMeta = new RequestMeta();
        ConfigPublishResponse configPublishResponse = ConfigPublishResponse.buildSuccessResponse();
        Mockito.when(proceedingJoinPoint.proceed()).thenReturn(configPublishResponse);
        //execute
        Object o = configChangeAspect.removeConfigAroundRpc(proceedingJoinPoint, request, requestMeta);
        //expect
        Mockito.verify(configChangePluginService, Mockito.times(0))
                .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        assertEquals(configPublishResponse, o);
    }
    
}
