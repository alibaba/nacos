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

package com.alibaba.nacos.plugin.auth.impl.oidc;

import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.OidcAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcAuthPluginServiceTest {
    
    private ConfigurableEnvironment originalEnvironment;
    
    @BeforeEach
    void setUp() {
        originalEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
        ReflectionTestUtils.setField(OidcAuthConfig.class, "instance", null);
        ReflectionTestUtils.setField(OidcAuthenticationManager.class, "instance", null);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(originalEnvironment);
        ReflectionTestUtils.setField(OidcAuthConfig.class, "instance", null);
        ReflectionTestUtils.setField(OidcAuthenticationManager.class, "instance", null);
    }
    
    @Test
    void testBasicPluginMetadata() {
        OidcAuthPluginService service = new OidcAuthPluginService();
        
        assertTrue(service.identityNames().contains(OidcProtocolConstants.AUTHORIZATION_HEADER));
        assertTrue(service.identityNames().contains(OidcProtocolConstants.ACCESS_TOKEN_PARAM));
        assertTrue(service.enableAuth(ActionTypes.READ, "config"));
        assertEquals(OidcProtocolConstants.AUTH_PLUGIN_TYPE, service.getAuthServiceName());
        assertTrue(service.isLoginEnabled());
        assertFalse(service.isAdminRequest());
    }
    
    @Test
    void testValidateIdentityDelegatesToProvider() {
        OidcAuthPluginService service = new OidcAuthPluginService();
        IdentityProvider identityProvider = mock(IdentityProvider.class);
        IdentityContext identityContext = new IdentityContext();
        Resource resource = Resource.EMPTY_RESOURCE;
        AuthResult<?> expected = AuthResult.successResult("ok");
        ReflectionTestUtils.setField(service, "identityProvider", identityProvider);
        ReflectionTestUtils.setField(service, "authorityProvider", mock(AuthorityProvider.class));
        when(identityProvider.validateIdentity(identityContext, resource)).thenReturn(expected);
        
        AuthResult<?> actual = service.validateIdentity(identityContext, resource);
        
        assertSame(expected, actual);
    }
    
    @Test
    void testValidateAuthorityDelegatesToProvider() {
        OidcAuthPluginService service = new OidcAuthPluginService();
        AuthorityProvider authorityProvider = mock(AuthorityProvider.class);
        IdentityContext identityContext = new IdentityContext();
        Permission permission = new Permission(Resource.EMPTY_RESOURCE, "read");
        AuthResult<?> expected = AuthResult.successResult("ok");
        ReflectionTestUtils.setField(service, "identityProvider", mock(IdentityProvider.class));
        ReflectionTestUtils.setField(service, "authorityProvider", authorityProvider);
        when(authorityProvider.validateAuthority(identityContext, permission)).thenReturn(expected);
        
        AuthResult<?> actual = service.validateAuthority(identityContext, permission);
        
        assertSame(expected, actual);
    }
    
    @Test
    void testValidateIdentityInitializesProviders() {
        OidcAuthPluginService service = new OidcAuthPluginService();
        
        AuthResult<?> result = service.validateIdentity(new IdentityContext(),
            Resource.EMPTY_RESOURCE);
        
        assertFalse(result.isSuccess());
    }
}
