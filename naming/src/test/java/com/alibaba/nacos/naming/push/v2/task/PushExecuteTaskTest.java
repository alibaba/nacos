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
import com.alibaba.nacos.core.remote.control.TpsMonitorManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.NoRequiredRetryException;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PushExecuteTaskTest {
    
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
    private TpsMonitorManager tpsMonitorManager;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Mock
    private Client client;
    
    @Mock
    private Subscriber subscriber;
    
    @Before
    public void setUp() {
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
        when(context.getBean(TpsMonitorManager.class)).thenReturn(tpsMonitorManager);
    }
    
    @Test
    public void testRunSuccessForPushAll() {
        PushDelayTask delayTask = new PushDelayTask(service, 0L);
        PushExecuteTask executeTask = new PushExecuteTask(service, delayTaskExecuteEngine, delayTask);
        executeTask.run();
        assertEquals(1, MetricsMonitor.getTotalPushMonitor().get());
    }
    
    @Test
    public void testRunSuccessForPushSingle() {
        PushDelayTask delayTask = new PushDelayTask(service, 0L, clientId);
        PushExecuteTask executeTask = new PushExecuteTask(service, delayTaskExecuteEngine, delayTask);
        executeTask.run();
        assertEquals(1, MetricsMonitor.getTotalPushMonitor().get());
    }
    
    @Test
    public void testRunFailedWithHandleException() {
        PushDelayTask delayTask = new PushDelayTask(service, 0L);
        PushExecuteTask executeTask = new PushExecuteTask(service, delayTaskExecuteEngine, delayTask);
        when(delayTaskExecuteEngine.getServiceStorage()).thenThrow(new RuntimeException());
        executeTask.run();
        assertEquals(0, MetricsMonitor.getFailedPushMonitor().get());
        verify(delayTaskExecuteEngine).addTask(eq(service), any(PushDelayTask.class));
    }
    
    @Test
    public void testRunFailedWithNoRetry() {
        PushDelayTask delayTask = new PushDelayTask(service, 0L);
        PushExecuteTask executeTask = new PushExecuteTask(service, delayTaskExecuteEngine, delayTask);
        pushExecutor.setShouldSuccess(false);
        pushExecutor.setFailedException(new NoRequiredRetryException());
        executeTask.run();
        assertEquals(1, MetricsMonitor.getFailedPushMonitor().get());
        verify(delayTaskExecuteEngine, never()).addTask(eq(service), any(PushDelayTask.class));
    }
    
    @Test
    public void testRunFailedWithRetry() {
        PushDelayTask delayTask = new PushDelayTask(service, 0L);
        PushExecuteTask executeTask = new PushExecuteTask(service, delayTaskExecuteEngine, delayTask);
        pushExecutor.setShouldSuccess(false);
        pushExecutor.setFailedException(new RuntimeException());
        executeTask.run();
        assertEquals(1, MetricsMonitor.getFailedPushMonitor().get());
        verify(delayTaskExecuteEngine).addTask(eq(service), any(PushDelayTask.class));
    }
}
