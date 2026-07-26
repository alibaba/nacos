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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.ldap.LdapAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.ldap.LdapPluginDependencyChecker;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LdapAuthPluginServiceTest {
    
    @Mock
    private IAuthenticationManager authenticationManager;
    
    @Test
    void testGetAuthServiceName() {
        assertEquals(AuthConstants.LDAP_AUTH_PLUGIN_TYPE,
            new LdapAuthPluginService().getAuthServiceName());
    }
    
    @Test
    void testConfigSpecDefinitionsAndApply() {
        LdapAuthPluginService service = new LdapAuthPluginService();
        List<ConfigItemDefinition> definitions = service.getConfigDefinitions();
        
        assertEquals(8, definitions.size());
        for (ConfigItemDefinition definition : definitions) {
            assertEquals(ConfigItemEffectMode.RESTART, definition.getEffectMode());
            assertEquals(1, definition.getAliases().size());
            assertFalse(definition.getAliases().get(0).startsWith("nacos.plugin.auth.ldap."));
        }
        ConfigItemDefinition password = definitions.stream()
            .filter(each -> LdapAuthPluginConfig.PASSWORD.equals(each.getKey())).findFirst()
            .orElseThrow(AssertionError::new);
        assertTrue(password.isSensitive());
        assertEquals(LdapAuthPluginConfig.DEFAULT_PASSWORD, password.getDefaultValue());
        
        Map<String, String> effectiveConfig = new LinkedHashMap<>();
        effectiveConfig.put(LdapAuthPluginConfig.URL, "ldaps://ldap.example.com:636");
        effectiveConfig.put(LdapAuthPluginConfig.BASE_DN, "dc=nacos,dc=io");
        effectiveConfig.put(LdapAuthPluginConfig.TIMEOUT, "5000");
        effectiveConfig.put(LdapAuthPluginConfig.USER_DN, "cn=reader,dc=nacos,dc=io");
        effectiveConfig.put(LdapAuthPluginConfig.PASSWORD, "secret");
        effectiveConfig.put(LdapAuthPluginConfig.FILTER_PREFIX, "mail");
        effectiveConfig.put(LdapAuthPluginConfig.CASE_SENSITIVE, "false");
        effectiveConfig.put(LdapAuthPluginConfig.IGNORE_PARTIAL_RESULT_EXCEPTION, "true");
        
        service.applyConfig(effectiveConfig);
        
        assertEquals(effectiveConfig, service.getCurrentConfig());
        assertEquals("ldaps://ldap.example.com:636", service.getConfig().getUrl());
    }
    
    @Test
    void testValidateIdentityLoadsLdapAuthenticationManager() throws AccessException {
        LdapAuthPluginService service = new LdapAuthPluginService();
        IdentityContext identityContext = new IdentityContext();
        identityContext.setParameter(AuthConstants.PARAM_USERNAME, "nacos");
        identityContext.setParameter(AuthConstants.PARAM_PASSWORD, "password");
        NacosUser user = new NacosUser("nacos", "token");
        when(authenticationManager.authenticate("nacos", "password")).thenReturn(user);
        
        try (MockedStatic<ApplicationUtils> applicationUtils = mockStatic(ApplicationUtils.class)) {
            applicationUtils.when(() -> ApplicationUtils.getBean(
                LdapPluginDependencyChecker.LDAP_AUTHENTICATION_MANAGER_BEAN_NAME,
                IAuthenticationManager.class)).thenReturn(authenticationManager);
            AuthResult<?> result = service.validateIdentity(identityContext, null);
            
            assertTrue(result.isSuccess());
            assertSame(user, result.getData());
            assertSame(user, identityContext.getParameter(AuthConstants.NACOS_USER_KEY));
        }
    }
}
