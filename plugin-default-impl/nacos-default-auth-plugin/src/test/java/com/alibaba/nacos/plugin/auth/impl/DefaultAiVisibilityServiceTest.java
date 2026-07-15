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

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.context.RequestContext;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import com.alibaba.nacos.plugin.visibility.spi.QueryAdvisor;
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DefaultAiVisibilityServiceTest {
    
    static {
        try {
            MockEnvironment environment = new MockEnvironment();
            environment.setProperty("nacos.core.auth.system.type", "nacos");
            environment.setProperty("nacos.core.auth.server.identity.key", "nacos");
            environment.setProperty("nacos.core.auth.server.identity.value", "nacos");
            environment.setProperty("nacos.core.auth.admin.enabled", "true");
            com.alibaba.nacos.sys.env.EnvUtil.setEnvironment(environment);
        } catch (Exception e) {
            // Ignore exception during static initialization
        }
    }
    
    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.core.auth.system.type", "nacos");
        environment.setProperty("nacos.core.auth.server.identity.key", "nacos");
        environment.setProperty("nacos.core.auth.server.identity.value", "nacos");
        environment.setProperty("nacos.core.auth.admin.enabled", "true");
        com.alibaba.nacos.sys.env.EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
        com.alibaba.nacos.sys.env.EnvUtil.setEnvironment(null);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void validateVisibilityShouldAllowWhenAuthDisabled() {
        DefaultAiVisibilityService service = new DefaultAiVisibilityService();
        Map<String, NacosAuthConfig> cachedConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        try {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                new HashMap<>());
            TestResource resource =
                new TestResource("public", "test", "skill", VisibilityConstants.SCOPE_PRIVATE,
                    "alice");
            ValidationResult result =
                service.validateVisibility("bob", VisibilityConstants.ACTION_READ, "ADMIN_API",
                    resource);
            assertTrue(result.isAllowed());
        } finally {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                cachedConfigMap);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void validateVisibilityShouldDenyWhenNoPermission() throws Exception {
        DefaultAiVisibilityService service = new DefaultAiVisibilityService();
        Map<String, NacosAuthConfig> cachedConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
        when(authConfig.getAuthScope()).thenReturn("ADMIN_API");
        when(authConfig.isAuthEnabled()).thenReturn(true);
        when(authConfig.getNacosAuthSystemType()).thenReturn("nacos");
        
        AuthPluginManager manager = mock(AuthPluginManager.class);
        AuthPluginService authService = mock(AuthPluginService.class);
        AuthResult denied = new AuthResult();
        denied.setSuccess(false);
        when(authService.validateAuthority(any(IdentityContext.class), any(Permission.class)))
            .thenReturn(denied);
        when(manager.findAuthServiceSpiImpl(anyString())).thenReturn(Optional.of(authService));
        
        try (MockedStatic<AuthPluginManager> managerStatic = mockStatic(AuthPluginManager.class)) {
            managerStatic.when(AuthPluginManager::getInstance).thenReturn(manager);
            Map<String, NacosAuthConfig> map = new HashMap<>();
            map.put("ADMIN_API", authConfig);
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                map);
            TestResource resource =
                new TestResource("public", "test", "skill", VisibilityConstants.SCOPE_PRIVATE,
                    "alice");
            ValidationResult result =
                service.validateVisibility("bob", VisibilityConstants.ACTION_READ, "ADMIN_API",
                    resource);
            assertFalse(result.isAllowed());
        } finally {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                cachedConfigMap);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void validateVisibilityShouldAllowWhenAuthPluginAllowsDefaultNamespaceResource()
        throws Exception {
        DefaultAiVisibilityService service = new DefaultAiVisibilityService();
        Map<String, NacosAuthConfig> cachedConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
        when(authConfig.getAuthScope()).thenReturn("ADMIN_API");
        when(authConfig.isAuthEnabled()).thenReturn(true);
        when(authConfig.getNacosAuthSystemType()).thenReturn("nacos");
        
        AuthPluginManager manager = mock(AuthPluginManager.class);
        AuthPluginService authService = mock(AuthPluginService.class);
        AuthResult allowed = new AuthResult();
        allowed.setSuccess(true);
        IdentityContext identityContext = new IdentityContext();
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(identityContext);
        when(authService.validateAuthority(any(IdentityContext.class), any(Permission.class)))
            .thenReturn(allowed);
        when(manager.findAuthServiceSpiImpl(anyString())).thenReturn(Optional.of(authService));
        
        try (MockedStatic<AuthPluginManager> managerStatic = mockStatic(AuthPluginManager.class)) {
            managerStatic.when(AuthPluginManager::getInstance).thenReturn(manager);
            Map<String, NacosAuthConfig> map = new HashMap<>();
            map.put("ADMIN_API", authConfig);
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                map);
            TestResource resource =
                new TestResource("", "skillE", "skill", VisibilityConstants.SCOPE_PRIVATE,
                    "alice");
            
            ValidationResult result =
                service.validateVisibility("bob", VisibilityConstants.ACTION_READ, "ADMIN_API",
                    resource);
            
            assertTrue(result.isAllowed());
        } finally {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                cachedConfigMap);
            RequestContextHolder.removeContext();
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void adviseQueryShouldReturnPublicAndOwnerForRead() {
        DefaultAiVisibilityService service = new DefaultAiVisibilityService();
        Map<String, NacosAuthConfig> cachedConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        try {
            NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
            when(authConfig.getAuthScope()).thenReturn("ADMIN_API");
            when(authConfig.isAuthEnabled()).thenReturn(true);
            Map<String, NacosAuthConfig> map = new HashMap<>();
            map.put("ADMIN_API", authConfig);
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                map);
            VisibilityQueryContext context = new VisibilityQueryContext();
            context.setNamespaceId("public");
            context.setResourceType("skill");
            QueryAdvisor advisor =
                service.adviseQuery("userA", VisibilityConstants.ACTION_READ, "ADMIN_API", context);
            assertEquals(BaseVisibilityPredicate.PUBLIC_AND_OWNER, advisor.getBasePredicate());
            assertEquals("skill", advisor.getAuthorizedPredicate().getResourceType());
            
            IdentityContext identityContext = new IdentityContext();
            identityContext.setParameter(AuthConstants.NACOS_USER_KEY, "notNacosUser");
            RequestContextHolder.getContext().getAuthContext().setIdentityContext(identityContext);
            QueryAdvisor nonNacosUserAdvisor =
                service.adviseQuery("userA", VisibilityConstants.ACTION_READ, "ADMIN_API", context);
            assertEquals(BaseVisibilityPredicate.PUBLIC_AND_OWNER,
                nonNacosUserAdvisor.getBasePredicate());
            
            QueryAdvisor blankIdentityAdvisor =
                service.adviseQuery("", VisibilityConstants.ACTION_READ, "ADMIN_API", context);
            assertEquals(BaseVisibilityPredicate.PUBLIC_AND_OWNER,
                blankIdentityAdvisor.getBasePredicate());
        } finally {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                cachedConfigMap);
            RequestContextHolder.removeContext();
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void adviseQueryShouldReturnAllForGlobalAdmin() {
        DefaultAiVisibilityService service = new DefaultAiVisibilityService();
        Map<String, NacosAuthConfig> cachedConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        try {
            NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
            when(authConfig.getAuthScope()).thenReturn("ADMIN_API");
            when(authConfig.isAuthEnabled()).thenReturn(true);
            Map<String, NacosAuthConfig> map = new HashMap<>();
            map.put("ADMIN_API", authConfig);
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                map);
            RequestContext requestContext = RequestContextHolder.getContext();
            IdentityContext identityContext = new IdentityContext();
            NacosUser admin = new NacosUser("adminUser");
            admin.setGlobalAdmin(true);
            identityContext.setParameter(AuthConstants.NACOS_USER_KEY, admin);
            requestContext.getAuthContext().setIdentityContext(identityContext);
            QueryAdvisor advisor = service.adviseQuery("adminUser", VisibilityConstants.ACTION_READ,
                "ADMIN_API", null);
            assertEquals(BaseVisibilityPredicate.ALL, advisor.getBasePredicate());
        } finally {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                cachedConfigMap);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void validateVisibilityShouldAllowForGlobalAdminOwnerAndPublicResource() {
        DefaultAiVisibilityService service = new DefaultAiVisibilityService();
        Map<String, NacosAuthConfig> cachedConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        try {
            NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
            when(authConfig.getAuthScope()).thenReturn("ADMIN_API");
            when(authConfig.isAuthEnabled()).thenReturn(true);
            Map<String, NacosAuthConfig> map = new HashMap<>();
            map.put("ADMIN_API", authConfig);
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                map);
            RequestContext requestContext = RequestContextHolder.getContext();
            IdentityContext identityContext = new IdentityContext();
            NacosUser admin = new NacosUser("adminUser");
            admin.setGlobalAdmin(true);
            identityContext.setParameter(AuthConstants.NACOS_USER_KEY, admin);
            requestContext.getAuthContext().setIdentityContext(identityContext);
            TestResource privateResource =
                new TestResource("public", "skillA", "skill", VisibilityConstants.SCOPE_PRIVATE,
                    "alice");
            assertTrue(service.validateVisibility("adminUser", VisibilityConstants.ACTION_READ,
                "ADMIN_API", privateResource).isAllowed());
            
            RequestContextHolder.removeContext();
            TestResource ownedResource =
                new TestResource("public", "skillB", "skill", VisibilityConstants.SCOPE_PRIVATE,
                    "bob");
            assertTrue(service.validateVisibility("bob", VisibilityConstants.ACTION_WRITE,
                "ADMIN_API", ownedResource).isAllowed());
            
            TestResource publicResource =
                new TestResource("public", "skillC", "skill", VisibilityConstants.SCOPE_PUBLIC,
                    "alice");
            assertTrue(service.validateVisibility("bob", VisibilityConstants.ACTION_READ,
                "ADMIN_API", publicResource).isAllowed());
        } finally {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                cachedConfigMap);
            RequestContextHolder.removeContext();
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void adviseQueryShouldReturnOwnerForWriteAndPublicForAnonymous() {
        DefaultAiVisibilityService service = new DefaultAiVisibilityService();
        Map<String, NacosAuthConfig> cachedConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        try {
            NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
            when(authConfig.getAuthScope()).thenReturn("ADMIN_API");
            when(authConfig.isAuthEnabled()).thenReturn(true);
            Map<String, NacosAuthConfig> map = new HashMap<>();
            map.put("ADMIN_API", authConfig);
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                map);
            
            QueryAdvisor writeAdvisor = service.adviseQuery("userA",
                VisibilityConstants.ACTION_WRITE, "ADMIN_API", null);
            assertEquals(BaseVisibilityPredicate.OWNER, writeAdvisor.getBasePredicate());
            
            QueryAdvisor anonymousReadAdvisor = service.adviseQuery(AuthConstants.ANONYMOUS_USER,
                VisibilityConstants.ACTION_READ, "ADMIN_API", null);
            assertEquals(BaseVisibilityPredicate.PUBLIC, anonymousReadAdvisor.getBasePredicate());
            assertNull(anonymousReadAdvisor.getAuthorizedPredicate().getResourceType());
            assertTrue(anonymousReadAdvisor.getAuthorizedPredicate().getResources().isEmpty());
        } finally {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                cachedConfigMap);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void validateVisibilityShouldDenyWhenAuthPluginMissingOrFails() {
        DefaultAiVisibilityService service = new DefaultAiVisibilityService();
        Map<String, NacosAuthConfig> cachedConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
        when(authConfig.getAuthScope()).thenReturn("ADMIN_API");
        when(authConfig.isAuthEnabled()).thenReturn(true);
        when(authConfig.getNacosAuthSystemType()).thenReturn("nacos");
        AuthPluginManager manager = mock(AuthPluginManager.class);
        TestResource resource =
            new TestResource("public", "skillD", "skill", VisibilityConstants.SCOPE_PRIVATE,
                "alice");
        try (MockedStatic<AuthPluginManager> managerStatic = mockStatic(AuthPluginManager.class)) {
            managerStatic.when(AuthPluginManager::getInstance).thenReturn(manager);
            Map<String, NacosAuthConfig> map = new HashMap<>();
            map.put("ADMIN_API", authConfig);
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                map);
            when(manager.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.empty())
                .thenThrow(new IllegalStateException("boom"));
            
            assertFalse(service.validateVisibility("bob", VisibilityConstants.ACTION_WRITE,
                "ADMIN_API", resource).isAllowed());
            assertFalse(service.validateVisibility("bob", VisibilityConstants.ACTION_WRITE,
                "ADMIN_API", resource).isAllowed());
        } finally {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                cachedConfigMap);
        }
    }
    
    @Test
    void resolveDefaultScopeForCreateShouldReturnPrivate() {
        DefaultAiVisibilityService service = new DefaultAiVisibilityService();
        String actual = service.resolveDefaultScopeForCreate("userA", "ADMIN_API", "skill");
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, actual);
    }
    
    @Test
    void getVisibilityServiceNameShouldReturnAuthPluginType() {
        assertEquals(AuthConstants.AUTH_PLUGIN_TYPE,
            new DefaultAiVisibilityService().getVisibilityServiceName());
    }
    
    static class TestResource extends VisibilityResource {
        
        private final String namespaceId;
        
        private final String resourceName;
        
        private final String resourceType;
        
        TestResource(String namespaceId, String resourceName, String resourceType, String scope,
            String owner) {
            this.namespaceId = namespaceId;
            this.resourceName = resourceName;
            this.resourceType = resourceType;
            setScope(scope);
            setOwner(owner);
        }
        
        @Override
        public String getNamespaceId() {
            return namespaceId;
        }
        
        @Override
        public String getResourceName() {
            return resourceName;
        }
        
        @Override
        public String getResourceType() {
            return resourceType;
        }
    }
}
