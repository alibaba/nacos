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

package com.alibaba.nacos.plugin.auth.impl.ldap;

import com.alibaba.nacos.plugin.auth.impl.LdapAuthPluginService;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.authenticate.LdapAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@SuppressWarnings("deprecation")
class LdapPluginConfigurationTest {
    
    @Test
    void testSelectImportsWithPresentDependency() {
        String[] imports = new LdapPluginImportSelector().selectImports(null);
        
        assertArrayEquals(new String[] {LdapPluginConfiguration.class.getName()}, imports);
    }
    
    @Test
    void testSelectImportsWithMissingDependency() {
        try (MockedStatic<LdapPluginDependencyChecker> checker =
            mockStatic(LdapPluginDependencyChecker.class)) {
            checker.when(LdapPluginDependencyChecker::hasRequiredDependency).thenReturn(false);
            
            String[] imports = new LdapPluginImportSelector().selectImports(null);
            
            assertArrayEquals(new String[] {LdapDependencyMissingConfiguration.class.getName()},
                imports);
        }
    }
    
    @Test
    void testMissingDependencyConfigurationCreatesFallbackManager() {
        IAuthenticationManager manager =
            new LdapDependencyMissingConfiguration().ldapAuthenticatoinManager();
        
        AccessException exception =
            assertThrows(AccessException.class, () -> manager.authenticate("nacos", "nacos"));
        
        assertTrue(exception.getErrMsg().contains("spring-ldap-core"));
    }
    
    @Test
    void testAutoConfigurationCanBeCreated() {
        assertNotNull(new LdapPluginAutoConfiguration());
    }
    
    @Test
    void testAuthPluginConfigCreatesBeans() throws Exception {
        LdapAuthPluginService pluginService = new LdapAuthPluginService();
        AuthPluginManager pluginManager = mock(AuthPluginManager.class);
        when(pluginManager.getAllPlugins()).thenReturn(Collections.singletonMap(
            AuthConstants.LDAP_AUTH_PLUGIN_TYPE, pluginService));
        NacosUserService userService = mock(NacosUserService.class);
        NacosRoleService roleService = mock(NacosRoleService.class);
        TokenManagerDelegate tokenManager = mock(TokenManagerDelegate.class);
        try (MockedStatic<AuthPluginManager> manager = mockStatic(AuthPluginManager.class)) {
            manager.when(AuthPluginManager::getInstance).thenReturn(pluginManager);
            LdapAuthPluginConfigProvider configProvider =
                LdapPluginConfiguration.ldapAuthPluginConfigProvider();
            LdapPluginConfiguration config = new LdapPluginConfiguration();
            LdapTemplateProvider templateProvider = config.ldapTemplateProvider(configProvider);
            LdapAuthenticationProvider provider = config.ldapAuthenticationProvider(
                templateProvider, userService, roleService, configProvider);
            IAuthenticationManager authenticationManager = config.ldapAuthenticatoinManager(
                templateProvider, userService, tokenManager, roleService, configProvider);
            GlobalAuthenticationConfigurerAdapter adapter =
                config.authenticationConfigurer(provider);
            AuthenticationManagerBuilder builder =
                new AuthenticationManagerBuilder(new ObjectPostProcessor<Object>() {
                    
                    @Override
                    public <O> O postProcess(O object) {
                        return object;
                    }
                });
            
            adapter.init(builder);
            
            assertSame(pluginService.getConfig(), configProvider.getConfig());
            assertInstanceOf(DefaultLdapTemplateProvider.class, templateProvider);
            assertTrue(provider.supports(UsernamePasswordAuthenticationToken.class));
            assertInstanceOf(LdapAuthenticationManager.class, authenticationManager);
            assertTrue(builder.isConfigured());
        }
    }
    
    @Test
    void testConfigProviderRejectsUnexpectedPlugin() {
        AuthPluginManager pluginManager = mock(AuthPluginManager.class);
        AuthPluginService unexpected = mock(AuthPluginService.class);
        when(pluginManager.getAllPlugins()).thenReturn(Collections.singletonMap(
            AuthConstants.LDAP_AUTH_PLUGIN_TYPE, unexpected));
        try (MockedStatic<AuthPluginManager> manager = mockStatic(AuthPluginManager.class)) {
            manager.when(AuthPluginManager::getInstance).thenReturn(pluginManager);
            assertThrows(IllegalStateException.class,
                LdapPluginConfiguration::ldapAuthPluginConfigProvider);
        }
    }
}
