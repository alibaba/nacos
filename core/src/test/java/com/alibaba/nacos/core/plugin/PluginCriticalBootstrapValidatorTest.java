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

package com.alibaba.nacos.core.plugin;

import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginCriticalBootstrapValidatorTest {
    
    @Test
    void testUtilityConstructor() throws Exception {
        Constructor<PluginCriticalBootstrapValidator> constructor =
            PluginCriticalBootstrapValidator.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
    
    @Test
    void testValidateWithServiceLoadedDefaults() {
        try (MockedStatic<NacosServiceLoader> loaderMock =
            mockStatic(NacosServiceLoader.class)) {
            loaderMock.when(() -> NacosServiceLoader.load(PluginTypePolicy.class))
                .thenReturn(Collections.emptyList());
            loaderMock.when(() -> NacosServiceLoader.load(PluginProvider.class))
                .thenReturn(Collections.emptyList());
            
            PluginCriticalBootstrapValidator.validate();
        }
    }
    
    @Test
    @SuppressWarnings("rawtypes")
    void testOnlyActiveCriticalProviderIsDiscovered() {
        PluginTypePolicyRegistry policyRegistry = mock(PluginTypePolicyRegistry.class);
        PluginProvider authProvider = mock(PluginProvider.class);
        PluginProvider traceProvider = mock(PluginProvider.class);
        when(authProvider.getPluginType()).thenReturn(PluginType.AUTH);
        when(traceProvider.getPluginType()).thenReturn(PluginType.TRACE);
        when(policyRegistry.isActive(PluginType.AUTH)).thenReturn(true);
        when(policyRegistry.supportsPreRefreshValidation(PluginType.AUTH)).thenReturn(true);
        when(policyRegistry.getRequiredPluginNames(PluginType.AUTH))
            .thenReturn(Collections.singleton("nacos"));
        when(authProvider.getAllPlugins())
            .thenReturn(Collections.singletonMap("nacos", new Object()));
        when(policyRegistry.isPluginEnabledByDefault(PluginType.AUTH, "nacos"))
            .thenReturn(true);
        
        PluginCriticalBootstrapValidator.validate(policyRegistry,
            Arrays.asList(authProvider, traceProvider));
        
        verify(authProvider).getAllPlugins();
        verify(traceProvider, never()).getAllPlugins();
        verify(policyRegistry).isPluginEnabledByDefault(PluginType.AUTH, "nacos");
    }
    
    @Test
    @SuppressWarnings("rawtypes")
    void testActiveCriticalProvidersAreDiscoveredInOrder() {
        PluginTypePolicyRegistry policyRegistry = mock(PluginTypePolicyRegistry.class);
        PluginProvider lowerPriorityProvider = mock(PluginProvider.class);
        PluginProvider higherPriorityProvider = mock(PluginProvider.class);
        when(lowerPriorityProvider.getPluginType()).thenReturn(PluginType.AUTH);
        when(lowerPriorityProvider.getOrder()).thenReturn(10);
        when(lowerPriorityProvider.getAllPlugins())
            .thenReturn(Collections.singletonMap("other", new Object()));
        when(higherPriorityProvider.getPluginType()).thenReturn(PluginType.AUTH);
        when(higherPriorityProvider.getOrder()).thenReturn(-10);
        when(higherPriorityProvider.getAllPlugins())
            .thenReturn(Collections.singletonMap("nacos", new Object()));
        when(policyRegistry.isActive(PluginType.AUTH)).thenReturn(true);
        when(policyRegistry.supportsPreRefreshValidation(PluginType.AUTH)).thenReturn(true);
        when(policyRegistry.getRequiredPluginNames(PluginType.AUTH))
            .thenReturn(Collections.singleton("nacos"));
        when(policyRegistry.isPluginEnabledByDefault(PluginType.AUTH, "nacos"))
            .thenReturn(true);
        when(policyRegistry.isPluginEnabledByDefault(PluginType.AUTH, "other"))
            .thenReturn(true);
        
        PluginCriticalBootstrapValidator.validate(policyRegistry,
            Arrays.asList(lowerPriorityProvider, higherPriorityProvider));
        
        InOrder providerOrder = inOrder(higherPriorityProvider, lowerPriorityProvider);
        providerOrder.verify(higherPriorityProvider).getAllPlugins();
        providerOrder.verify(lowerPriorityProvider).getAllPlugins();
    }
    
    @Test
    @SuppressWarnings("rawtypes")
    void testDeferredCriticalProviderIsNotDiscoveredBeforeRefresh() {
        PluginTypePolicyRegistry policyRegistry = mock(PluginTypePolicyRegistry.class);
        PluginProvider provider = mock(PluginProvider.class);
        when(provider.getPluginType()).thenReturn(PluginType.AUTH);
        when(policyRegistry.isActive(PluginType.AUTH)).thenReturn(true);
        when(policyRegistry.supportsPreRefreshValidation(PluginType.AUTH)).thenReturn(false);
        
        PluginCriticalBootstrapValidator.validate(policyRegistry,
            Collections.singletonList(provider));
        
        verify(provider, never()).getAllPlugins();
    }
    
    @Test
    @SuppressWarnings("rawtypes")
    void testValidationErrorBlocksStartup() {
        PluginTypePolicyRegistry policyRegistry = mock(PluginTypePolicyRegistry.class);
        PluginProvider provider = mock(PluginProvider.class);
        when(provider.getPluginType()).thenReturn(PluginType.AUTH);
        when(policyRegistry.isActive(PluginType.AUTH)).thenReturn(true);
        when(policyRegistry.supportsPreRefreshValidation(PluginType.AUTH)).thenReturn(true);
        when(provider.getAllPlugins()).thenReturn(Collections.emptyMap());
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> PluginCriticalBootstrapValidator.validate(policyRegistry,
                Collections.singletonList(provider)));
        
        assertTrue(exception.getMessage().contains("no discovered implementation"));
    }
    
    @Test
    @SuppressWarnings("rawtypes")
    void testProviderIdentificationFailureIsIgnored() {
        PluginTypePolicyRegistry policyRegistry = mock(PluginTypePolicyRegistry.class);
        PluginProvider provider = mock(PluginProvider.class);
        when(provider.getPluginType()).thenThrow(new IllegalStateException("broken type"));
        when(policyRegistry.isActive(PluginType.AUTH)).thenReturn(true);
        when(policyRegistry.supportsPreRefreshValidation(PluginType.AUTH)).thenReturn(true);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> PluginCriticalBootstrapValidator.validate(policyRegistry,
                Collections.singletonList(provider)));
        
        assertTrue(exception.getMessage().contains("no discovered implementation"));
    }
    
    @Test
    @SuppressWarnings("rawtypes")
    void testActiveProviderDiscoveryFailureBlocksStartup() {
        PluginTypePolicyRegistry policyRegistry = mock(PluginTypePolicyRegistry.class);
        PluginProvider provider = mock(PluginProvider.class);
        when(provider.getPluginType()).thenReturn(PluginType.AUTH);
        when(policyRegistry.isActive(PluginType.AUTH)).thenReturn(true);
        when(policyRegistry.supportsPreRefreshValidation(PluginType.AUTH)).thenReturn(true);
        when(provider.getAllPlugins()).thenThrow(new IllegalStateException("broken provider"));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> PluginCriticalBootstrapValidator.validate(policyRegistry,
                Collections.singletonList(provider)));
        
        assertTrue(exception.getMessage().contains("auth"));
        assertTrue(exception.getMessage().contains(provider.getClass().getName()));
    }
    
    @Test
    @SuppressWarnings("rawtypes")
    void testNullPluginMapsAndInstancesAreIgnored() {
        PluginTypePolicyRegistry policyRegistry = mock(PluginTypePolicyRegistry.class);
        PluginProvider nullMapProvider = mock(PluginProvider.class);
        PluginProvider nullInstanceProvider = mock(PluginProvider.class);
        when(nullMapProvider.getPluginType()).thenReturn(PluginType.AUTH);
        when(nullInstanceProvider.getPluginType()).thenReturn(PluginType.AUTH);
        when(policyRegistry.isActive(PluginType.AUTH)).thenReturn(true);
        when(policyRegistry.supportsPreRefreshValidation(PluginType.AUTH)).thenReturn(true);
        when(nullMapProvider.getAllPlugins()).thenReturn(null);
        Map<String, Object> plugins = new LinkedHashMap<>();
        plugins.put("null", null);
        when(nullInstanceProvider.getAllPlugins()).thenReturn(plugins);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> PluginCriticalBootstrapValidator.validate(policyRegistry,
                Arrays.asList(nullMapProvider, nullInstanceProvider)));
        
        assertTrue(exception.getMessage().contains("no discovered implementation"));
        verify(policyRegistry, never()).isPluginEnabledByDefault(
            org.mockito.ArgumentMatchers.eq(PluginType.AUTH),
            org.mockito.ArgumentMatchers.anyString());
    }
}
