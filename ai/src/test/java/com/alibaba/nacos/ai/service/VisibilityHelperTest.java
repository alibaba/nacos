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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityPluginManager;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class VisibilityHelperTest {
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    private MockedStatic<VisibilityPluginManager> visibilityManagerStatic;
    
    private VisibilityPluginManager visibilityPluginManager;
    
    @BeforeEach
    void setUp() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        resetCachedVisibilityServiceName();
        visibilityPluginManager = mock(VisibilityPluginManager.class);
        visibilityManagerStatic = mockStatic(VisibilityPluginManager.class);
        visibilityManagerStatic.when(VisibilityPluginManager::getInstance)
            .thenReturn(visibilityPluginManager);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (visibilityManagerStatic != null) {
            visibilityManagerStatic.close();
        }
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
        resetCachedVisibilityServiceName();
    }
    
    @Test
    void resolveDefaultScopeForCreateShouldFallbackToPrivateWhenPluginAbsent() {
        when(visibilityPluginManager.findVisibilityService(anyString()))
            .thenReturn(Optional.empty());
        String actual = VisibilityHelper.resolveDefaultScopeForCreate("skill");
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, actual);
    }
    
    @Test
    void resolveDefaultScopeForCreateShouldUsePluginScopeAndNormalizeUppercase() {
        VisibilityService visibilityService = mock(VisibilityService.class);
        when(visibilityService.resolveDefaultScopeForCreate(anyString(), anyString(), anyString()))
            .thenReturn("public");
        when(visibilityPluginManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(visibilityService));
        String actual = VisibilityHelper.resolveDefaultScopeForCreate("skill");
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, actual);
    }
    
    @Test
    void resolveDefaultScopeForCreateShouldFallbackToPrivateWhenPluginReturnsBlank() {
        VisibilityService visibilityService = mock(VisibilityService.class);
        when(visibilityService.resolveDefaultScopeForCreate(anyString(), anyString(), anyString()))
            .thenReturn("  ");
        when(visibilityPluginManager.findVisibilityService(anyString()))
            .thenReturn(Optional.of(visibilityService));
        String actual = VisibilityHelper.resolveDefaultScopeForCreate("skill");
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, actual);
    }
    
    private static void resetCachedVisibilityServiceName() throws Exception {
        Field field = VisibilityHelper.class.getDeclaredField("cachedVisibilityServiceName");
        field.setAccessible(true);
        field.set(null, null);
    }
}
