/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v2.task;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.NoRequiredRetryException;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class PushExecuteTaskTest {
    
    private final Service service = Service.newService("N", "G", "S");
    
    private final String clientId = "testClient";
    
    private final FixturePushExecutor pushExecutor = new FixturePushExecutor();
    
    @Mock
    private PushDelayTaskExecuteEngine delayTaskExecuteEngine;
    
    @Mock
    private ClientManager clientManager;
    
    @Mock
    private ClientServiceIndexesManager indexesManager;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Mock
    private Client client;
    
    @Mock
    private Subscriber subscriber;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        MetricsMonitor.resetAll();
        when(indexesManager.getAllClientsSubscribeService(service)).thenReturn(Collections.singletonList(clientId));
        when(clientManager.getClient(clientId)).thenReturn(client);
        when(client.getSubscriber(service)).thenReturn(subscriber);
        when(serviceStorage.getPushData(service)).thenReturn(new ServiceInfo("G@@S"));
        when(delayTaskExecuteEngine.getClientManager()).thenReturn(clientManager);
        when(delayTaskExecuteEngine.getIndexesManager()).thenReturn(indexesManager);
        when(delayTaskExecuteEngine.getPushExecutor()).thenReturn(pushExecutor);
        when(delayTaskExecuteEngine.getServiceStorage()).thenReturn(serviceStorage);
        when(delayTaskExecuteEngine.getMetadataManager()).thenReturn(metadataManager);
        when(metadataManager.getServiceMetadata(service)).thenReturn(Optional.empty());
        ApplicationUtils.injectContext(context);
    }
    
    @Test
    void testRunSuccessForPushAll() {
        PushDelayTask delayTask = new PushDelayTask(service, 0L);
        PushExecuteTask executeTask = new PushExecuteTask(service, delayTaskExecuteEngine, delayTask);
        executeTask.run();
        assertEquals(1, MetricsMonitor.getTotalPushMonitor().get());
    }
    
    @Test
    void testRunSuccessForPushSingle() {
        PushDelayTask delayTask = new PushDelayTask(service, 0L, clientId);
        PushExecuteTask executeTask = new PushExecuteTask(service, delayTaskExecuteEngine, delayTask);
        executeTask.run();
        assertEquals(1, MetricsMonitor.getTotalPushMonitor().get());
    }
    
    @Test
    void testRunFailedWithHandleException() {
        PushDelayTask delayTask = new PushDelayTask(service, 0L);
        PushExecuteTask executeTask = new PushExecuteTask(service, delayTaskExecuteEngine, delayTask);
        when(delayTaskExecuteEngine.getServiceStorage()).thenThrow(new RuntimeException());
        executeTask.run();
        assertEquals(0, MetricsMonitor.getFailedPushMonitor().get());
        verify(delayTaskExecuteEngine).addTask(eq(service), any(PushDelayTask.class));
    }
    
    @Test
    void testRunFailedWithNoRetry() {
        PushDelayTask delayTask = new PushDelayTask(service, 0L);
        PushExecuteTask executeTask = new PushExecuteTask(service, delayTaskExecuteEngine, delayTask);
        pushExecutor.setShouldSuccess(false);
        pushExecutor.setFailedException(new NoRequiredRetryException());
        executeTask.run();
        assertEquals(1, MetricsMonitor.getFailedPushMonitor().get());
        verify(delayTaskExecuteEngine, never()).addTask(eq(service), any(PushDelayTask.class));
    }
    
    @Test
    void testRunFailedWithRetry() {
        PushDelayTask delayTask = new PushDelayTask(service, 0L);
        PushExecuteTask executeTask = new PushExecuteTask(service, delayTaskExecuteEngine, delayTask);
        pushExecutor.setShouldSuccess(false);
        pushExecutor.setFailedException(new RuntimeException());
        executeTask.run();
        assertEquals(1, MetricsMonitor.getFailedPushMonitor().get());
        verify(delayTaskExecuteEngine).addTask(eq(service), any(PushDelayTask.class));
    }
}
