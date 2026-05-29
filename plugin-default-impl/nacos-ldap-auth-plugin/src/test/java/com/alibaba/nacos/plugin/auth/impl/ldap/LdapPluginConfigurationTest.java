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

import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.authenticate.LdapAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@SuppressWarnings("deprecation")
class LdapPluginConfigurationTest {
    
    @Test
    void testSelectImportsWithPresentDependency() {
        String[] imports = new LdapPluginImportSelector().selectImports(null);
        
        assertArrayEquals(new String[] {LdapAuthPluginConfig.class.getName()}, imports);
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
        LdapAuthPluginConfig config = new LdapAuthPluginConfig();
        configureLdapProperties(config);
        LdapContextSource contextSource = config.ldapContextSource();
        LdapTemplate ldapTemplate = config.ldapTemplate(contextSource);
        NacosUserService userService = mock(NacosUserService.class);
        NacosRoleService roleService = mock(NacosRoleService.class);
        TokenManagerDelegate tokenManager = mock(TokenManagerDelegate.class);
        
        LdapAuthenticationProvider provider =
            config.ldapAuthenticationProvider(ldapTemplate, userService, roleService);
        IAuthenticationManager manager =
            config.ldapAuthenticatoinManager(ldapTemplate, userService, tokenManager, roleService);
        GlobalAuthenticationConfigurerAdapter adapter = config.authenticationConfigurer(provider);
        AuthenticationManagerBuilder builder =
            new AuthenticationManagerBuilder(new ObjectPostProcessor<Object>() {
                
                @Override
                public <O> O postProcess(O object) {
                    return null;
                }
            });
        
        adapter.init(builder);
        
        assertArrayEquals(new String[] {"ldap://localhost:389"}, contextSource.getUrls());
        assertEquals(Boolean.TRUE,
            ReflectionTestUtils.getField(ldapTemplate, "ignorePartialResultException"));
        assertTrue(provider.supports(UsernamePasswordAuthenticationToken.class));
        assertInstanceOf(LdapAuthenticationManager.class, manager);
        assertTrue(builder.isConfigured());
    }
    
    private void configureLdapProperties(LdapAuthPluginConfig config) {
        ReflectionTestUtils.setField(config, "ldapUrl", "ldap://localhost:389");
        ReflectionTestUtils.setField(config, "ldapBaseDc", "dc=example,dc=org");
        ReflectionTestUtils.setField(config, "ldapTimeOut", "1000");
        ReflectionTestUtils.setField(config, "userDn", "cn=admin,dc=example,dc=org");
        ReflectionTestUtils.setField(config, "password", "password");
        ReflectionTestUtils.setField(config, "filterPrefix", "uid");
        ReflectionTestUtils.setField(config, "caseSensitive", false);
        ReflectionTestUtils.setField(config, "ignorePartialResultException", true);
    }
    
    private static class NoOpObjectPostProcessor implements ObjectPostProcessor<Object> {
        
        @Override
        public <O> O postProcess(O object) {
            return object;
        }
    }
}
