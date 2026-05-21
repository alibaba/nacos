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
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.BasicContext;
import com.alibaba.nacos.plugin.config.ConfigChangePluginManager;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeExecuteTypes;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;
import com.alibaba.nacos.plugin.config.spi.ConfigChangePluginService;
import com.alibaba.nacos.sys.utils.PropertiesUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ConfigChangeAspectTest {
    
    ConfigChangeAspect configChangeAspect;
    
    ConfigChangeConfigs configChangeConfigs;
    
    @MockitoBean
    ConfigChangePluginService configChangePluginService;
    
    MockedStatic<PropertiesUtil> propertiesStatic;
    
    MockedStatic<RequestUtil> requestUtilMockedStatic;
    
    @MockitoBean
    private ProceedingJoinPoint pjp;
    
    @MockitoBean
    private ConfigForm configForm;
    
    @MockitoBean
    private ConfigRequestInfo configRequestInfo;
    
    @BeforeEach
    void before() {
        RequestContextHolder.getContext().getBasicContext().setRequestProtocol(null);
        RequestContextHolder.getContext().getBasicContext().setRequestTarget(null);
        
        //mock config change service enabled.
        propertiesStatic = Mockito.mockStatic(PropertiesUtil.class);
        requestUtilMockedStatic = Mockito.mockStatic(RequestUtil.class);
        Properties properties = new Properties();
        properties.put("mockedConfigChangeService.enabled", "true");
        propertiesStatic.when(() -> PropertiesUtil.getPropertiesWithPrefix(any(),
            eq(ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX))).thenReturn(properties);
        requestUtilMockedStatic
            .when(() -> RequestUtil.getSrcUserName(any(HttpServletRequest.class)))
            .thenReturn("mockedUser");
        Mockito.when(configChangePluginService.getServiceType())
            .thenReturn("mockedConfigChangeService");
        Mockito.when(configChangePluginService.pointcutMethodNames())
            .thenReturn(ConfigChangePointCutTypes.values());
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        
        ConfigChangePluginManager.join(configChangePluginService);
        
        configChangeConfigs = new ConfigChangeConfigs();
        configChangeAspect = new ConfigChangeAspect(configChangeConfigs);
    }
    
    @AfterEach
    void after() {
        RequestContextHolder.getContext().getBasicContext().setRequestProtocol(null);
        RequestContextHolder.getContext().getBasicContext().setRequestTarget(null);
        
        propertiesStatic.close();
        requestUtilMockedStatic.close();
        ConfigChangePluginManager.reset();
    }
    
    @Test
    void testPublishOrUpdateConfigAround() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
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
        verify(configChangePluginService, Mockito.times(1))
            .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        //expect join point processed success.
        assertEquals("Success", o);
    }
    
    @Test
    void testRemoveConfigByIdAround() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        
        String dataId = "dataId1";
        String group = "group1";
        String namespaceId = "namespaceId1";
        String tag = "tag1";
        String clientIp = "127.0.0.1";
        String srcUser = "mockedUser";
        String srcType = "http";
        
        when(pjp.getArgs())
            .thenReturn(new Object[] {dataId, group, namespaceId, tag, clientIp, srcUser, srcType});
        Mockito.when(pjp.proceed(any())).thenReturn("mock success return");
        Object o = configChangeAspect.removeConfigByIdAround(pjp);
        Thread.sleep(20L);
        
        // expect service executed.
        verify(configChangePluginService, Mockito.times(1))
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
        
        when(pjp.getArgs())
            .thenReturn(new Object[] {dataId, group, namespaceId, tag, clientIp, srcUser, srcType});
        propertiesStatic.when(() -> PropertiesUtil.getPropertiesWithPrefix(any(),
            eq(ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX))).thenReturn(properties);
        configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        assertFalse(Boolean.parseBoolean(
            configChangeConfigs.getPluginProperties("mockedConfigChangeService")
                .getProperty("enabled")));
        
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        Mockito.when(configChangePluginService.getServiceType())
            .thenReturn("mockedConfigChangeService");
        ConfigPublishResponse configPublishResponse = ConfigPublishResponse.buildSuccessResponse();
        Mockito.when(pjp.proceed()).thenReturn(configPublishResponse);
        //execute
        Object o = configChangeAspect.removeConfigByIdAround(pjp);
        //expect
        verify(configChangePluginService, Mockito.times(0))
            .execute(any(ConfigChangeRequest.class), any(ConfigChangeResponse.class));
        assertEquals(configPublishResponse, o);
    }
    
    @Test
    void testBeforePluginFailurePreventsProceed() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        Mockito.doAnswer(invocation -> {
            ConfigChangeResponse response = invocation.getArgument(1);
            response.setSuccess(false);
            response.setMsg("Before plugin failed");
            return null;
        }).when(configChangePluginService).execute(any(), any());
        
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn("dataId");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        Object result = configChangeAspect.publishOrUpdateConfigAround(pjp);
        
        verify(pjp, never()).proceed();
        assertEquals(false, result);
        verify(configChangePluginService).execute(any(), any());
    }
    
    @Test
    void testProceedThrowsExceptionHandled() throws Throwable {
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn("dataId");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(pjp.proceed(any())).thenThrow(new RuntimeException("Proceed error"));
        
        Object result = configChangeAspect.publishOrUpdateConfigAround(pjp);
        
        verify(configChangePluginService, Mockito.timeout(1000).times(1)).execute(any(), any());
        assertEquals(false, result);
    }
    
    @Test
    void testAfterPluginExecutedAsynchronously() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        Mockito.doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(configChangePluginService).execute(any(), any());
        
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn("dataId");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(pjp.proceed()).thenReturn("Success");
        
        Object result = configChangeAspect.publishOrUpdateConfigAround(pjp);
        
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        verify(configChangePluginService).execute(any(), any());
        assertEquals(null, result);
    }
    
    @Test
    void testRpcSourceTypeHandling() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configRequestInfo.getSrcType()).thenReturn("rpc");
        when(configForm.getDataId()).thenReturn("dataId");
        when(pjp.proceed(any())).thenReturn("Success");
        
        configChangeAspect.publishOrUpdateConfigAround(pjp);
        ArgumentCaptor<ConfigChangeRequest> requestCaptor =
            ArgumentCaptor.forClass(ConfigChangeRequest.class);
        verify(configChangePluginService).execute(requestCaptor.capture(), any());
        assertEquals(ConfigChangePointCutTypes.PUBLISH_BY_RPC,
            requestCaptor.getValue().getRequestType());
    }
    
    @Test
    void testRequestProtocolTakesPrecedenceOverSourceTypeForPublish() throws Throwable {
        RequestContextHolder.getContext().getBasicContext()
            .setRequestProtocol(BasicContext.HTTP_PROTOCOL);
        RequestContextHolder.getContext().getBasicContext().setRequestTarget("POST /v3/cs/config");
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configRequestInfo.getSrcType()).thenReturn("rpc");
        when(configForm.getDataId()).thenReturn("dataId");
        when(pjp.proceed(any())).thenReturn("Success");
        
        configChangeAspect.publishOrUpdateConfigAround(pjp);
        
        ArgumentCaptor<ConfigChangeRequest> requestCaptor =
            ArgumentCaptor.forClass(ConfigChangeRequest.class);
        verify(configChangePluginService).execute(requestCaptor.capture(), any());
        assertEquals(ConfigChangePointCutTypes.PUBLISH_BY_HTTP,
            requestCaptor.getValue().getRequestType());
    }
    
    @Test
    void testRequestProtocolTakesPrecedenceOverSourceTypeForRemove() throws Throwable {
        RequestContextHolder.getContext().getBasicContext()
            .setRequestProtocol(BasicContext.GRPC_PROTOCOL);
        RequestContextHolder.getContext().getBasicContext().setRequestTarget("ConfigRemoveRequest");
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        when(pjp.getArgs()).thenReturn(
            new Object[] {"dataId", "group", "namespaceId", null, "127.0.0.1", "nacos", "http"});
        when(pjp.proceed(any())).thenReturn("Success");
        
        configChangeAspect.removeConfigByIdAround(pjp);
        
        ArgumentCaptor<ConfigChangeRequest> requestCaptor =
            ArgumentCaptor.forClass(ConfigChangeRequest.class);
        verify(configChangePluginService).execute(requestCaptor.capture(), any());
        assertEquals(ConfigChangePointCutTypes.REMOVE_BY_RPC,
            requestCaptor.getValue().getRequestType());
    }
    
    @Test
    void testPublishWithUnknownSourceTypeUsesUnknownPointcut() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configRequestInfo.getSrcType()).thenReturn(null);
        when(configForm.getDataId()).thenReturn("dataId");
        when(pjp.proceed(any())).thenReturn("Success");
        
        configChangeAspect.publishOrUpdateConfigAround(pjp);
        
        ArgumentCaptor<ConfigChangeRequest> requestCaptor =
            ArgumentCaptor.forClass(ConfigChangeRequest.class);
        verify(configChangePluginService).execute(requestCaptor.capture(), any());
        assertEquals(ConfigChangePointCutTypes.PUBLISH_BY_UNKNOWN,
            requestCaptor.getValue().getRequestType());
    }
    
    @Test
    void testRemoveWithUnknownSourceTypeUsesUnknownPointcut() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        when(pjp.getArgs()).thenReturn(
            new Object[] {"dataId", "group", "namespaceId", null, "127.0.0.1", "nacos", null});
        when(pjp.proceed(any())).thenReturn("Success");
        
        configChangeAspect.removeConfigByIdAround(pjp);
        
        ArgumentCaptor<ConfigChangeRequest> requestCaptor =
            ArgumentCaptor.forClass(ConfigChangeRequest.class);
        verify(configChangePluginService).execute(requestCaptor.capture(), any());
        assertEquals(ConfigChangePointCutTypes.REMOVE_BY_UNKNOWN,
            requestCaptor.getValue().getRequestType());
    }
    
    @Test
    void testRemoveHttpSourceTypeUsesRemovePointcut() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        when(pjp.getArgs()).thenReturn(
            new Object[] {"dataId", "group", "namespaceId", null, "127.0.0.1", "nacos", "http"});
        when(pjp.proceed(any())).thenReturn("Success");
        
        configChangeAspect.removeConfigByIdAround(pjp);
        ArgumentCaptor<ConfigChangeRequest> requestCaptor =
            ArgumentCaptor.forClass(ConfigChangeRequest.class);
        verify(configChangePluginService).execute(requestCaptor.capture(), any());
        assertEquals(ConfigChangePointCutTypes.REMOVE_BY_HTTP,
            requestCaptor.getValue().getRequestType());
    }
    
    @Test
    void testRemoveRpcSourceTypeUsesRemovePointcut() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        when(pjp.getArgs()).thenReturn(
            new Object[] {"dataId", "group", "namespaceId", null, "127.0.0.1", "nacos", "rpc"});
        when(pjp.proceed(any())).thenReturn("Success");
        
        configChangeAspect.removeConfigByIdAround(pjp);
        ArgumentCaptor<ConfigChangeRequest> requestCaptor =
            ArgumentCaptor.forClass(ConfigChangeRequest.class);
        verify(configChangePluginService).execute(requestCaptor.capture(), any());
        assertEquals(ConfigChangePointCutTypes.REMOVE_BY_RPC,
            requestCaptor.getValue().getRequestType());
    }
    
    @Test
    void testPublishConfigWithBetaIps() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn("dataId");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(configRequestInfo.getBetaIps()).thenReturn("10.0.0.1,10.0.0.2");
        when(pjp.proceed(any())).thenReturn("Success");
        
        Object result = configChangeAspect.publishOrUpdateConfigAround(pjp);
        Thread.sleep(20L);
        
        ArgumentCaptor<ConfigChangeRequest> captor =
            ArgumentCaptor.forClass(ConfigChangeRequest.class);
        verify(configChangePluginService).execute(captor.capture(), any());
        assertEquals("beta", captor.getValue().getArg("grayName"));
        assertEquals("10.0.0.1,10.0.0.2", captor.getValue().getArg("grayRuleExp"));
    }
    
    @Test
    void testPublishConfigWithTag() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn("dataId");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(configRequestInfo.getBetaIps()).thenReturn(null);
        when(configForm.getTag()).thenReturn("myTag");
        when(pjp.proceed(any())).thenReturn("Success");
        
        Object result = configChangeAspect.publishOrUpdateConfigAround(pjp);
        Thread.sleep(20L);
        
        ArgumentCaptor<ConfigChangeRequest> captor =
            ArgumentCaptor.forClass(ConfigChangeRequest.class);
        verify(configChangePluginService).execute(captor.capture(), any());
        assertEquals("tag_myTag", captor.getValue().getArg("grayName"));
    }
    
    @Test
    void testRemoveConfigByIdWithRpcType() throws Throwable {
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        
        when(pjp.getArgs()).thenReturn(
            new Object[] {"d", "g", "ns", "tag", "127.0.0.1", "user", "rpc"});
        when(pjp.proceed(any())).thenReturn("ok");
        
        configChangeAspect.removeConfigByIdAround(pjp);
        Thread.sleep(20L);
        
        ArgumentCaptor<ConfigChangeRequest> captor =
            ArgumentCaptor.forClass(ConfigChangeRequest.class);
        verify(configChangePluginService).execute(captor.capture(), any());
        assertEquals(ConfigChangePointCutTypes.REMOVE_BY_RPC,
            captor.getValue().getRequestType());
    }
    
    @Test
    void testNoPluginsEnabled() throws Throwable {
        Properties properties = new Properties();
        properties.put("mockedConfigChangeService.enabled", "false");
        propertiesStatic.when(() -> PropertiesUtil.getPropertiesWithPrefix(any(), any()))
            .thenReturn(properties);
        configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(pjp.proceed()).thenReturn("Success");
        
        Object result = configChangeAspect.publishOrUpdateConfigAround(pjp);
        
        verify(configChangePluginService, never()).execute(any(), any());
        assertEquals("Success", result);
    }
    
    @Test
    void testDisabledPluginSkippedWhenEnabledPluginExists() throws Throwable {
        ConfigChangePluginService disabledService =
            Mockito.mock(ConfigChangePluginService.class);
        Mockito.when(disabledService.getServiceType())
            .thenReturn("disabledConfigChangeService");
        Mockito.when(disabledService.pointcutMethodNames())
            .thenReturn(ConfigChangePointCutTypes.values());
        Mockito.when(disabledService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        ConfigChangePluginManager.join(disabledService);
        
        Properties properties = new Properties();
        properties.put("mockedConfigChangeService.enabled", "true");
        properties.put("disabledConfigChangeService.enabled", "false");
        propertiesStatic.when(() -> PropertiesUtil.getPropertiesWithPrefix(any(), any()))
            .thenReturn(properties);
        configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn("dataId");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(pjp.proceed(any())).thenReturn("Success");
        
        Object result = configChangeAspect.publishOrUpdateConfigAround(pjp);
        
        verify(disabledService, never()).execute(any(), any());
        verify(configChangePluginService).execute(any(), any());
        assertEquals("Success", result);
    }
    
    @Test
    void testBeforePluginCanReplaceProceedArgs() throws Throwable {
        Object[] originalArgs = new Object[] {configForm, configRequestInfo};
        ConfigForm newConfigForm = Mockito.mock(ConfigForm.class);
        ConfigRequestInfo newConfigRequestInfo = Mockito.mock(ConfigRequestInfo.class);
        Object[] replacedArgs = new Object[] {newConfigForm, newConfigRequestInfo};
        
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE);
        Mockito.doAnswer(invocation -> {
            ConfigChangeResponse response = invocation.getArgument(1);
            response.setArgs(replacedArgs);
            return null;
        }).when(configChangePluginService).execute(any(), any());
        when(pjp.getArgs()).thenReturn(originalArgs);
        when(configForm.getDataId()).thenReturn("dataId");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(pjp.proceed(replacedArgs)).thenReturn("replaced");
        
        Object result = configChangeAspect.publishOrUpdateConfigAround(pjp);
        
        verify(pjp).proceed(replacedArgs);
        assertEquals("replaced", result);
    }
    
    @Test
    void testAfterPluginExceptionHandled() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        Mockito.when(configChangePluginService.executeType())
            .thenReturn(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE);
        Mockito.doAnswer(invocation -> {
            latch.countDown();
            throw new RuntimeException("async plugin failed");
        }).when(configChangePluginService).execute(any(), any());
        
        when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn("dataId");
        when(configRequestInfo.getSrcType()).thenReturn("http");
        when(pjp.proceed(any())).thenReturn("Success");
        
        Object result = configChangeAspect.publishOrUpdateConfigAround(pjp);
        
        assertEquals("Success", result);
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    }
    
    @Test
    void testNullPluginListTreatedAsNoPlugin() throws Throwable {
        try (MockedStatic<ConfigChangePluginManager> pluginManagerMockedStatic =
            Mockito.mockStatic(ConfigChangePluginManager.class)) {
            pluginManagerMockedStatic.when(() -> ConfigChangePluginManager
                .findPluginServicesByPointcut(ConfigChangePointCutTypes.PUBLISH_BY_HTTP))
                .thenReturn(null);
            when(pjp.getArgs()).thenReturn(new Object[] {configForm, configRequestInfo});
            when(configRequestInfo.getSrcType()).thenReturn("http");
            when(pjp.proceed()).thenReturn("Success");
            
            Object result = configChangeAspect.publishOrUpdateConfigAround(pjp);
            
            verify(configChangePluginService, never()).execute(any(), any());
            assertEquals("Success", result);
        }
    }
    
}
