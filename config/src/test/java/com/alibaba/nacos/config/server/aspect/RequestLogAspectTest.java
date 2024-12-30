/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class RequestLogAspectTest {
    
    @Mock
    private ProceedingJoinPoint pjp;
    
    @Mock
    private ConfigForm configForm;
    
    @Mock
    private ConfigRequestInfo configRequestInfo;
    
    @Mock
    private ConfigQueryChainRequest chainRequest;
    
    @Mock
    private ConfigBatchListenRequest request;
    
    @Mock
    private Response response;
    
    @Mock
    private RequestMeta meta;
    
    private RequestLogAspect requestLogAspect;
    
    @BeforeEach
    void setUp() {
        requestLogAspect = new RequestLogAspect();
    }
    
    @Test
    void testInterfacePublishConfig() throws Throwable {
        when(pjp.getArgs()).thenReturn(new Object[]{configForm, configRequestInfo});
        when(configForm.getDataId()).thenReturn("dataId");
        when(configForm.getGroup()).thenReturn("group");
        when(configForm.getNamespaceId()).thenReturn("namespaceId");
        when(configForm.getContent()).thenReturn("content");
        when(configRequestInfo.getSrcIp()).thenReturn("127.0.0.1");
        
        when(pjp.proceed()).thenReturn("Success");
        AtomicInteger publishMonitor = MetricsMonitor.getPublishMonitor();
        int initialValue = publishMonitor.get();
        
        Object result = requestLogAspect.interfacePublishConfig(pjp);
        
        verify(pjp, times(1)).proceed();
        assertEquals("Success", result);
        
        assertEquals(initialValue + 1, publishMonitor.get());
    }
    
    @Test
    void testInterfaceGetConfig() throws Throwable {
        when(pjp.getArgs()).thenReturn(new Object[]{chainRequest});
        when(chainRequest.getDataId()).thenReturn("dataId");
        when(chainRequest.getGroup()).thenReturn("group");
        when(chainRequest.getTenant()).thenReturn("tenant");
        
        when(pjp.proceed()).thenReturn("ConfigData");
        
        AtomicInteger configMonitor = MetricsMonitor.getConfigMonitor();
        int initialValue = configMonitor.get();
        
        Object result = requestLogAspect.interfaceGetConfig(pjp);

        verify(pjp, times(1)).proceed();
        assertEquals("ConfigData", result);
        assertEquals(initialValue + 1, configMonitor.get());
    }
    
    @Test
    void testInterfaceDeleteConfig() throws Throwable {
        String dataId = "dataId1";
        String group = "group1";
        String namespaceId = "namespaceId1";
        String tag = "tag1";
        String clientIp = "127.0.0.1";
        when(pjp.getArgs()).thenReturn(new Object[]{dataId, group, namespaceId, tag, clientIp});
        
        when(pjp.proceed()).thenReturn("Success");
        AtomicInteger configMonitor = MetricsMonitor.getConfigMonitor();
        int initialValue = configMonitor.get();
        
        Object result = requestLogAspect.interfaceRemoveConfig(pjp);
        
        verify(pjp, times(1)).proceed();
        assertEquals("Success", result);
        assertEquals(initialValue + 1, configMonitor.get());
    }
    
    @Test
    void testInterfaceListenConfigRpc() throws Throwable {
        when(meta.getClientIp()).thenReturn("127.0.0.1");
        when(request.isListen()).thenReturn(true);
        
        when(pjp.proceed()).thenReturn(new ConfigChangeBatchListenResponse());
        AtomicInteger configMonitor = MetricsMonitor.getConfigMonitor();
        int initialValue = configMonitor.get();
        
        Response result = (Response) requestLogAspect.interfaceListenConfigRpc(pjp, request, meta);

        assertEquals(result.getResultCode(), 200);
        assertEquals(initialValue + 1, configMonitor.get());
    }
}
