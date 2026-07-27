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

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.OidcProtocolConstants;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.AuthorizationCodeHandler;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.constant.OidcConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcAuthPluginServiceTest {
    
    @Test
    void testBasicPluginMetadata() {
        OidcAuthPluginService service = new OidcAuthPluginService();
        
        assertTrue(service.identityNames().contains(OidcProtocolConstants.AUTHORIZATION_HEADER));
        assertTrue(service.identityNames().contains(OidcProtocolConstants.ACCESS_TOKEN_PARAM));
        assertTrue(service.enableAuth(ActionTypes.READ, "config"));
        assertEquals(OidcProtocolConstants.AUTH_PLUGIN_TYPE, service.getAuthServiceName());
        assertTrue(service.isLoginEnabled());
        assertFalse(service.isAdminRequest());
        assertFalse(service.isConfigurationValid());
    }
    
    @Test
    void testConfigDefinitionsExposeAllRestartItemsAndLegacyAliases() {
        OidcAuthPluginService service = new OidcAuthPluginService();
        List<ConfigItemDefinition> definitions = service.getConfigDefinitions();
        
        assertEquals(14, definitions.size());
        for (ConfigItemDefinition definition : definitions) {
            assertEquals(ConfigItemEffectMode.RESTART, definition.getEffectMode());
            assertEquals(1, definition.getAliases().size());
            assertTrue(definition.getAliases().get(0)
                .startsWith(OidcConstants.CONFIG_PREFIX));
        }
        ConfigItemDefinition secret = definition(definitions, OidcAuthPluginConfig.CLIENT_SECRET);
        assertTrue(secret.isSensitive());
        assertFalse(definition(definitions, OidcAuthPluginConfig.CLIENT_ID).isSensitive());
    }
    
    @Test
    void testApplyConfigAtomicallyReplacesCurrentConfig() {
        OidcAuthPluginService service = new OidcAuthPluginService();
        Map<String, String> config = new LinkedHashMap<>();
        config.put(OidcAuthPluginConfig.ISSUER_URI, "http://issuer");
        config.put(OidcAuthPluginConfig.CLIENT_ID, "client");
        config.put(OidcAuthPluginConfig.CLIENT_SECRET, "secret");
        config.put(OidcAuthPluginConfig.AUTHORIZATION_TIMEOUT_MS, "1234");
        
        service.applyConfig(config);
        
        assertTrue(service.isConfigurationValid());
        assertEquals("client", service.getConfig().getClientId());
        assertEquals("secret", service.getCurrentConfig()
            .get(OidcAuthPluginConfig.CLIENT_SECRET));
        assertEquals("1234", service.getCurrentConfig()
            .get(OidcAuthPluginConfig.AUTHORIZATION_TIMEOUT_MS));
        assertThrows(IllegalArgumentException.class,
            () -> service.applyConfig(Map.of(OidcAuthPluginConfig.AUTHORIZATION_TIMEOUT_MS,
                "invalid")));
        assertEquals("client", service.getConfig().getClientId());
    }
    
    @Test
    void testDefaultRuntimeDelegatesAuthenticationAndAuthorization() {
        OidcAuthPluginService service = new OidcAuthPluginService();
        IdentityContext context = new IdentityContext();
        
        AuthResult<?> identity = service.validateIdentity(context, Resource.EMPTY_RESOURCE);
        AuthResult<?> authority = service.validateAuthority(context,
            new Permission(Resource.EMPTY_RESOURCE, "read"));
        
        assertFalse(identity.isSuccess());
        assertEquals(401, identity.getErrorCode());
        assertFalse(authority.isSuccess());
        assertEquals(403, authority.getErrorCode());
    }
    
    @Test
    void testLoginDelegatesFailWithoutProviderConfiguration() {
        OidcAuthPluginService service = new OidcAuthPluginService();
        
        assertThrows(AccessException.class,
            () -> service.buildAuthorizationUrl("http://nacos/callback"));
        assertThrows(AccessException.class,
            () -> service.exchangeCodeForUser("code", "bad-state", "http://nacos/callback"));
        assertNull(service.buildLogoutUrl("id-token", "http://nacos"));
    }
    
    @Test
    void testLoginMethodsDelegateToCurrentRuntime() throws Exception {
        OidcAuthPluginService service = new OidcAuthPluginService();
        AuthorizationCodeHandler handler = mock(AuthorizationCodeHandler.class);
        Object runtime = ReflectionTestUtils.getField(service, "runtime");
        ReflectionTestUtils.setField(runtime, "authorizationCodeHandler", handler);
        OidcUser user = new OidcUser();
        when(handler.buildAuthorizationUrl("http://nacos/callback"))
            .thenReturn("http://idp/authorize");
        when(handler.exchangeCodeForUser("code", "state", "http://nacos/callback"))
            .thenReturn(user);
        when(handler.buildLogoutUrl("id-token", "http://nacos"))
            .thenReturn("http://idp/logout");
        
        assertEquals("http://idp/authorize",
            service.buildAuthorizationUrl("http://nacos/callback"));
        assertEquals(user,
            service.exchangeCodeForUser("code", "state", "http://nacos/callback"));
        assertEquals("http://idp/logout", service.buildLogoutUrl("id-token", "http://nacos"));
    }
    
    private ConfigItemDefinition definition(List<ConfigItemDefinition> definitions, String key) {
        return definitions.stream().filter(each -> key.equals(each.getKey())).findFirst()
            .orElseThrow(IllegalStateException::new);
    }
}
