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

package com.alibaba.nacos.naming.push.v2.task;

import com.alibaba.nacos.api.naming.remote.request.AbstractFuzzyWatchNotifyRequest;
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchChangeNotifyRequest;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.naming.push.v2.NoRequiredRetryException;
import com.alibaba.nacos.naming.push.v2.PushConfig;
import com.alibaba.nacos.naming.push.v2.executor.PushExecutor;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.ADD_SERVICE;
import static com.alibaba.nacos.naming.push.v2.task.FuzzyWatchPushDelayTaskEngine.getTaskKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuzzyWatchChangeNotifyExecuteTaskTest {
    
    private static final String SERVICE_KEY = "namespace@@group@@service";
    
    private static final String CLIENT_ID = "connection-1";
    
    @Mock
    private FuzzyWatchPushDelayTaskEngine delayTaskEngine;
    
    @Mock
    private PushExecutor pushExecutor;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testRunPushesChangeNotifyRequest() {
        when(delayTaskEngine.getPushExecutor()).thenReturn(pushExecutor);
        FuzzyWatchChangeNotifyExecuteTask task =
            new FuzzyWatchChangeNotifyExecuteTask(delayTaskEngine, SERVICE_KEY, ADD_SERVICE,
                CLIENT_ID);
        ArgumentCaptor<AbstractFuzzyWatchNotifyRequest> requestCaptor =
            ArgumentCaptor.forClass(AbstractFuzzyWatchNotifyRequest.class);
        ArgumentCaptor<PushCallBack> callbackCaptor = ArgumentCaptor.forClass(PushCallBack.class);
        
        task.run();
        
        verify(pushExecutor).doFuzzyWatchNotifyPushWithCallBack(eq(CLIENT_ID),
            requestCaptor.capture(), callbackCaptor.capture());
        NamingFuzzyWatchChangeNotifyRequest request =
            assertInstanceOf(NamingFuzzyWatchChangeNotifyRequest.class,
                requestCaptor.getValue());
        assertEquals(SERVICE_KEY, request.getServiceKey());
        assertEquals(ADD_SERVICE, request.getChangedType());
        assertEquals(PushConfig.getInstance().getPushTaskTimeout(),
            callbackCaptor.getValue().getTimeout());
    }
    
    @Test
    void testCallbackSuccessAndFailRetryBehavior() {
        when(delayTaskEngine.getPushExecutor()).thenReturn(pushExecutor);
        PushCallBack callback = captureCallback();
        
        callback.onSuccess();
        callback.onFail(new NoRequiredRetryException());
        verify(delayTaskEngine, never()).addTask(anyString(), any(AbstractDelayTask.class));
        
        callback.onFail(new RuntimeException("push failed"));
        
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FuzzyWatchChangeNotifyTask> taskCaptor =
            ArgumentCaptor.forClass(FuzzyWatchChangeNotifyTask.class);
        verify(delayTaskEngine).addTask(keyCaptor.capture(), taskCaptor.capture());
        FuzzyWatchChangeNotifyTask retryTask = taskCaptor.getValue();
        assertEquals(SERVICE_KEY, retryTask.getServiceKey());
        assertEquals(ADD_SERVICE, retryTask.getChangedType());
        assertEquals(CLIENT_ID, retryTask.getClientId());
        assertEquals(PushConfig.getInstance().getPushTaskRetryDelay(), retryTask.getDelay());
        assertEquals(getTaskKey(retryTask), keyCaptor.getValue());
    }
    
    private PushCallBack captureCallback() {
        FuzzyWatchChangeNotifyExecuteTask task =
            new FuzzyWatchChangeNotifyExecuteTask(delayTaskEngine, SERVICE_KEY, ADD_SERVICE,
                CLIENT_ID);
        ArgumentCaptor<PushCallBack> callbackCaptor = ArgumentCaptor.forClass(PushCallBack.class);
        task.run();
        verify(pushExecutor).doFuzzyWatchNotifyPushWithCallBack(eq(CLIENT_ID),
            any(AbstractFuzzyWatchNotifyRequest.class), callbackCaptor.capture());
        return callbackCaptor.getValue();
    }
}
