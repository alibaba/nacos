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

package com.alibaba.nacos.client.config.listener.impl;

import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.http.MetricsHttpAgent;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.utils.ParamUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.Mockito.mock;

public class ClientWorkerTest {
    
    @Mock
    ScheduledExecutorService scheduledExecutorService;
    
    private ClientWorker clientWorker;
    
    private List<Listener> listeners;
    
    private final String dataId = "data";
    
    private final String group = "group";
    
    private final String currentLongingTaskCount = "currentLongingTaskCount";
    
    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        clientWorker = new ClientWorker(mock(MetricsHttpAgent.class), mock(ConfigFilterChainManager.class),
                mock(Properties.class));
        try {
            Field executorServiceField = clientWorker.getClass().getDeclaredField("executorService");
            executorServiceField.setAccessible(true);
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(executorServiceField, executorServiceField.getModifiers() & ~Modifier.FINAL);
            executorServiceField.set(clientWorker, scheduledExecutorService);
            Listener listener = new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }
                
                @Override
                public void receiveConfigInfo(String configInfo) {
                
                }
            };
            listeners = Arrays.asList(listener);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void testAddLongPollNumberThreads() {
        try {
            for (int i = 0; i < ParamUtil.getPerTaskConfigSize(); i++) {
                clientWorker.addTenantListeners(dataId + i, group, listeners);
            }
            Field currentLongingTaskCountField = clientWorker.getClass().getDeclaredField(currentLongingTaskCount);
            currentLongingTaskCountField.setAccessible(true);
            Assert.assertEquals(currentLongingTaskCount, (int) currentLongingTaskCountField.getDouble(clientWorker), 1);
            for (int i = (int) ParamUtil.getPerTaskConfigSize(); i < ParamUtil.getPerTaskConfigSize() * 2; i++) {
                clientWorker.addTenantListeners(dataId + i, group, listeners);
            }
            Assert.assertEquals(currentLongingTaskCount, (int) currentLongingTaskCountField.getDouble(clientWorker), 2);
        } catch (NacosException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void testReduceLongPollNumberThreads() {
        try {
            for (int i = 0; i < ParamUtil.getPerTaskConfigSize() * 3; i++) {
                clientWorker.addTenantListeners(dataId + i, group, listeners);
            }
            Field currentLongingTaskCountField = clientWorker.getClass().getDeclaredField(currentLongingTaskCount);
            currentLongingTaskCountField.setAccessible(true);
            Assert.assertEquals(currentLongingTaskCount, (int) currentLongingTaskCountField.getDouble(clientWorker), 3);
            
            for (int i = (int) ParamUtil.getPerTaskConfigSize(); i < ParamUtil.getPerTaskConfigSize() * 2; i++) {
                clientWorker.removeTenantListener(dataId + i, group, listeners.get(0));
            }
            Assert.assertEquals(currentLongingTaskCount, (int) currentLongingTaskCountField.getDouble(clientWorker), 2);
        } catch (NacosException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }
    
}
