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

package com.alibaba.nacos.plugin.auth.impl.oidc.config;

import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.OidcAuthPluginService;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class OidcPluginAutoConfigurationTest {
    
    @Test
    void testGetOidcAuthPluginServiceAndCreateController() {
        AuthPluginManager manager = mock(AuthPluginManager.class);
        OidcAuthPluginService service = new OidcAuthPluginService();
        when(manager.getAllPlugins()).thenReturn(Collections.singletonMap(
            OidcProtocolConstants.AUTH_PLUGIN_TYPE, service));
        
        try (MockedStatic<AuthPluginManager> managerStatic = mockStatic(AuthPluginManager.class)) {
            managerStatic.when(AuthPluginManager::getInstance).thenReturn(manager);
            
            assertSame(service, OidcPluginAutoConfiguration.getOidcAuthPluginService());
            assertNotNull(new OidcPluginAutoConfiguration().oidcLoginController());
        }
    }
    
    @Test
    void testGetOidcAuthPluginServiceRejectsMissingPlugin() {
        AuthPluginManager manager = mock(AuthPluginManager.class);
        when(manager.getAllPlugins()).thenReturn(Collections.singletonMap(
            OidcProtocolConstants.AUTH_PLUGIN_TYPE, mock(AuthPluginService.class)));
        
        try (MockedStatic<AuthPluginManager> managerStatic = mockStatic(AuthPluginManager.class)) {
            managerStatic.when(AuthPluginManager::getInstance).thenReturn(manager);
            
            assertThrows(IllegalStateException.class,
                OidcPluginAutoConfiguration::getOidcAuthPluginService);
        }
    }
}
