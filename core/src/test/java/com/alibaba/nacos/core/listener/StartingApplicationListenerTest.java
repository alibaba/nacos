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

package com.alibaba.nacos.core.listener;

import com.alibaba.nacos.core.listener.startup.NacosStartUp;
import com.alibaba.nacos.core.listener.startup.NacosStartUpManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * {@link StartingApplicationListener} unit test.
 */
@ExtendWith(MockitoExtension.class)
class StartingApplicationListenerTest {
    
    @Test
    void startingDelegatesToCurrentStartUp() {
        StartingApplicationListener listener = new StartingApplicationListener();
        NacosStartUp mockStartUp = mock(NacosStartUp.class);
        try (
            MockedStatic<NacosStartUpManager> managerMock = mockStatic(NacosStartUpManager.class)) {
            managerMock.when(NacosStartUpManager::getCurrentStartUp).thenReturn(mockStartUp);
            listener.starting();
            verify(mockStartUp).starting();
        }
    }
    
    @Test
    void environmentPreparedDelegatesToCurrentStartUp() {
        StartingApplicationListener listener = new StartingApplicationListener();
        NacosStartUp mockStartUp = mock(NacosStartUp.class);
        ConfigurableEnvironment environment = new MockEnvironment();
        try (
            MockedStatic<NacosStartUpManager> managerMock = mockStatic(NacosStartUpManager.class)) {
            managerMock.when(NacosStartUpManager::getCurrentStartUp).thenReturn(mockStartUp);
            listener.environmentPrepared(environment);
            verify(mockStartUp).makeWorkDir();
            verify(mockStartUp).injectEnvironment(environment);
            verify(mockStartUp).loadPreProperties(environment);
            verify(mockStartUp).initSystemProperty();
        }
    }
    
    @Test
    void contextPreparedDelegatesLogStartingInfo() {
        StartingApplicationListener listener = new StartingApplicationListener();
        NacosStartUp mockStartUp = mock(NacosStartUp.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        try (
            MockedStatic<NacosStartUpManager> managerMock = mockStatic(NacosStartUpManager.class)) {
            managerMock.when(NacosStartUpManager::getCurrentStartUp).thenReturn(mockStartUp);
            listener.contextPrepared(context);
            verify(mockStartUp).logStartingInfo(any());
        }
    }
    
    @Test
    void contextLoadedDelegatesCustomEnvironment() {
        StartingApplicationListener listener = new StartingApplicationListener();
        NacosStartUp mockStartUp = mock(NacosStartUp.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        try (
            MockedStatic<NacosStartUpManager> managerMock = mockStatic(NacosStartUpManager.class)) {
            managerMock.when(NacosStartUpManager::getCurrentStartUp).thenReturn(mockStartUp);
            listener.contextLoaded(context);
            verify(mockStartUp).customEnvironment();
        }
    }
    
    @Test
    void startedDelegatesToCurrentStartUp() {
        StartingApplicationListener listener = new StartingApplicationListener();
        NacosStartUp mockStartUp = mock(NacosStartUp.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        try (
            MockedStatic<NacosStartUpManager> managerMock = mockStatic(NacosStartUpManager.class)) {
            managerMock.when(NacosStartUpManager::getCurrentStartUp).thenReturn(mockStartUp);
            listener.started(context);
            verify(mockStartUp).started();
            verify(mockStartUp).logStarted(any());
        }
    }
    
    @Test
    void failedCallsReverseStartedListAndLogs() {
        StartingApplicationListener listener = new StartingApplicationListener();
        NacosStartUp mockStartUp = mock(NacosStartUp.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        Throwable exception = new RuntimeException("startup fail");
        try (
            MockedStatic<NacosStartUpManager> managerMock = mockStatic(NacosStartUpManager.class)) {
            managerMock.when(NacosStartUpManager::getReverseStartedList)
                .thenReturn(java.util.Collections.singletonList(mockStartUp));
            listener.failed(context, exception);
            verify(mockStartUp).failed(eq(exception), eq(context));
        }
    }
}
