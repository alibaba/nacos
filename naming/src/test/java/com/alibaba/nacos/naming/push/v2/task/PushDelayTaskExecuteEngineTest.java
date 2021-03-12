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
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v2.PushDataWrapper;
import com.alibaba.nacos.naming.push.v2.executor.PushExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PushDelayTaskExecuteEngineTest {
    
    @Mock
    private ClientManager clientManager;
    
    @Mock
    private ClientServiceIndexesManager indexesManager;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private PushExecutor pushExecutor;
    
    @Mock
    private Client client;
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private SwitchDomain switchDomain;
    
    private final Service service = Service.newService("N", "G", "S");
    
    private final String clientId = "testClient";
    
    private PushDelayTaskExecuteEngine executeEngine;
    
    @Before
    public void setUp() throws Exception {
        when(serviceStorage.getPushData(service)).thenReturn(new ServiceInfo("G@@S"));
        when(indexesManager.getAllClientsSubscribeService(service)).thenReturn(Collections.singletonList(clientId));
        when(clientManager.getClient(clientId)).thenReturn(client);
        when(client.getSubscriber(service)).thenReturn(subscriber);
        when(switchDomain.isPushEnabled()).thenReturn(true);
        executeEngine = new PushDelayTaskExecuteEngine(clientManager, indexesManager, serviceStorage, metadataManager, pushExecutor,
                switchDomain);
    }
    
    @After
    public void tearDown() throws Exception {
        executeEngine.shutdown();
    }
    
    @Test
    public void testAddTask() throws InterruptedException {
        PushDelayTask pushDelayTask = new PushDelayTask(service, 0L);
        executeEngine.addTask(service, pushDelayTask);
        TimeUnit.MILLISECONDS.sleep(200L);
        verify(pushExecutor).doPushWithCallback(anyString(), any(Subscriber.class), any(PushDataWrapper.class),
                any(PushCallBack.class));
    }
}
