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
import com.alibaba.nacos.core.plugin.PluginCriticalBootstrapValidator;
import com.alibaba.nacos.core.plugin.PreContextPluginInitializer;
import com.alibaba.nacos.core.plugin.StandardPluginInitializer;
import com.alibaba.nacos.sys.env.DeploymentType;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockConstruction;

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
        when(mockStartUp.startUpPhase()).thenReturn(NacosStartUp.CORE_START_UP_PHASE);
        try (
            MockedStatic<NacosStartUpManager> managerMock = mockStatic(NacosStartUpManager.class);
            MockedStatic<EnvUtil> envUtilMock = mockStatic(EnvUtil.class);
            MockedStatic<PluginCriticalBootstrapValidator> validatorMock =
                mockStatic(PluginCriticalBootstrapValidator.class);
            MockedConstruction<PreContextPluginInitializer> initializerMock =
                mockConstruction(PreContextPluginInitializer.class)) {
            managerMock.when(NacosStartUpManager::getCurrentStartUp).thenReturn(mockStartUp);
            envUtilMock.when(EnvUtil::getDeploymentType).thenReturn(DeploymentType.MERGED);
            listener.contextLoaded(context);
            verify(initializerMock.constructed().get(0)).initialize();
            verify(mockStartUp).customEnvironment();
            validatorMock.verify(PluginCriticalBootstrapValidator::validate);
        }
    }
    
    @Test
    void contextLoadedSkipsValidationOutsideNacosDeployment() {
        StartingApplicationListener listener = new StartingApplicationListener();
        NacosStartUp mockStartUp = mock(NacosStartUp.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(mockStartUp.startUpPhase()).thenReturn(NacosStartUp.CORE_START_UP_PHASE);
        try (
            MockedStatic<NacosStartUpManager> managerMock = mockStatic(NacosStartUpManager.class);
            MockedStatic<EnvUtil> envUtilMock = mockStatic(EnvUtil.class);
            MockedStatic<PluginCriticalBootstrapValidator> validatorMock =
                mockStatic(PluginCriticalBootstrapValidator.class);
            MockedConstruction<PreContextPluginInitializer> initializerMock =
                mockConstruction(PreContextPluginInitializer.class)) {
            managerMock.when(NacosStartUpManager::getCurrentStartUp).thenReturn(mockStartUp);
            envUtilMock.when(EnvUtil::getDeploymentType).thenReturn(null);
            listener.contextLoaded(context);
            verify(mockStartUp).customEnvironment();
            validatorMock.verifyNoInteractions();
            assertTrue(initializerMock.constructed().isEmpty());
        }
    }
    
    @Test
    void contextLoadedSkipsValidationOutsideCorePhase() {
        StartingApplicationListener listener = new StartingApplicationListener();
        NacosStartUp mockStartUp = mock(NacosStartUp.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(mockStartUp.startUpPhase()).thenReturn(NacosStartUp.WEB_START_UP_PHASE);
        try (
            MockedStatic<NacosStartUpManager> managerMock = mockStatic(NacosStartUpManager.class);
            MockedStatic<EnvUtil> envUtilMock = mockStatic(EnvUtil.class);
            MockedStatic<PluginCriticalBootstrapValidator> validatorMock =
                mockStatic(PluginCriticalBootstrapValidator.class);
            MockedConstruction<PreContextPluginInitializer> initializerMock =
                mockConstruction(PreContextPluginInitializer.class)) {
            managerMock.when(NacosStartUpManager::getCurrentStartUp).thenReturn(mockStartUp);
            envUtilMock.when(EnvUtil::getDeploymentType).thenReturn(DeploymentType.MERGED);
            listener.contextLoaded(context);
            verify(mockStartUp).customEnvironment();
            validatorMock.verifyNoInteractions();
            assertTrue(initializerMock.constructed().isEmpty());
        }
    }
    
    @Test
    void startedDelegatesToCurrentStartUp() {
        StartingApplicationListener listener = new StartingApplicationListener();
        NacosStartUp mockStartUp = mock(NacosStartUp.class);
        StandardPluginInitializer initializer = mock(StandardPluginInitializer.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StandardPluginInitializer> initializerProvider =
            mock(ObjectProvider.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getBeanProvider(StandardPluginInitializer.class))
            .thenReturn(initializerProvider);
        when(initializerProvider.getIfAvailable()).thenReturn(initializer);
        try (
            MockedStatic<NacosStartUpManager> managerMock = mockStatic(NacosStartUpManager.class)) {
            managerMock.when(NacosStartUpManager::getCurrentStartUp).thenReturn(mockStartUp);
            listener.started(context);
            InOrder inOrder = inOrder(initializer, mockStartUp);
            inOrder.verify(initializer).initialize();
            inOrder.verify(mockStartUp).started();
            verify(mockStartUp).logStarted(any());
        }
    }
    
    @Test
    void startedSupportsContextWithoutPluginManager() {
        StartingApplicationListener listener = new StartingApplicationListener();
        NacosStartUp mockStartUp = mock(NacosStartUp.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StandardPluginInitializer> initializerProvider =
            mock(ObjectProvider.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getBeanProvider(StandardPluginInitializer.class))
            .thenReturn(initializerProvider);
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
