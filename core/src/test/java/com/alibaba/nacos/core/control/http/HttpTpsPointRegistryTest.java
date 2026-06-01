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

package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.core.remote.HealthCheckRequestHandler;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HttpTpsPointRegistry} unit test.
 *
 * @author liuzunfei
 */
@ExtendWith(MockitoExtension.class)
class HttpTpsPointRegistryTest {
    
    @Mock
    private ApplicationContext applicationContext;
    
    @Mock
    private RequestMappingHandlerMapping requestMappingHandlerMapping;
    
    @Mock
    private ControlManagerCenter controlManagerCenter;
    
    @Mock
    private TpsControlManager tpsControlManager;
    
    private MockedStatic<ControlManagerCenter> controlManagerCenterMockedStatic;
    
    private HttpTpsPointRegistry registry;
    
    @BeforeEach
    void setUp() throws Exception {
        registry = new HttpTpsPointRegistry();
        controlManagerCenterMockedStatic = Mockito.mockStatic(ControlManagerCenter.class);
        controlManagerCenterMockedStatic.when(ControlManagerCenter::getInstance)
            .thenReturn(controlManagerCenter);
        when(controlManagerCenter.getTpsControlManager()).thenReturn(tpsControlManager);
    }
    
    @AfterEach
    void tearDown() {
        if (controlManagerCenterMockedStatic != null) {
            controlManagerCenterMockedStatic.close();
        }
    }
    
    @Test
    void testOnApplicationEventRegistersTpsPoints() throws Exception {
        HealthCheckRequestHandler handlerBean = new HealthCheckRequestHandler();
        Method handleMethod = HealthCheckRequestHandler.class.getMethod("handle",
            HealthCheckRequest.class, RequestMeta.class);
        HandlerMethod handlerMethod = new HandlerMethod(handlerBean, handleMethod);
        RequestMappingInfo mappingInfo = Mockito.mock(RequestMappingInfo.class);
        Map<RequestMappingInfo, HandlerMethod> handlerMethods =
            Collections.singletonMap(mappingInfo, handlerMethod);
        
        when(applicationContext.getBean(eq("requestMappingHandlerMapping"),
            eq(RequestMappingHandlerMapping.class)))
            .thenReturn(requestMappingHandlerMapping);
        when(requestMappingHandlerMapping.getHandlerMethods()).thenReturn(handlerMethods);
        
        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);
        registry.onApplicationEvent(event);
        
        verify(tpsControlManager).registerTpsPoint("HealthCheck");
    }
    
    @Test
    void testOnApplicationEventSecondCallSkipsInit() throws Exception {
        HealthCheckRequestHandler handlerBean = new HealthCheckRequestHandler();
        Method handleMethod = HealthCheckRequestHandler.class.getMethod("handle",
            HealthCheckRequest.class, RequestMeta.class);
        HandlerMethod handlerMethod = new HandlerMethod(handlerBean, handleMethod);
        RequestMappingInfo mappingInfo = Mockito.mock(RequestMappingInfo.class);
        Map<RequestMappingInfo, HandlerMethod> handlerMethods =
            Collections.singletonMap(mappingInfo, handlerMethod);
        
        when(applicationContext.getBean(eq("requestMappingHandlerMapping"),
            eq(RequestMappingHandlerMapping.class)))
            .thenReturn(requestMappingHandlerMapping);
        when(requestMappingHandlerMapping.getHandlerMethods()).thenReturn(handlerMethods);
        
        ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);
        registry.onApplicationEvent(event);
        registry.onApplicationEvent(event);
        
        verify(tpsControlManager, Mockito.times(1)).registerTpsPoint(anyString());
    }
}
