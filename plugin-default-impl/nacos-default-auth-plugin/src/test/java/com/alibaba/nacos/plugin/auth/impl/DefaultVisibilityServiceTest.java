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
import com.alibaba.nacos.plugin.auth.impl.visibility.VisibilityGrantService;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import com.alibaba.nacos.plugin.visibility.spi.QueryAdvisor;
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
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

class DefaultVisibilityServiceTest {
    
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
        ApplicationUtils.injectContext(null);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"absent", "alice", "bob"})
    void explicitIdentityUsesCurrentPermissionsWithoutBorrowingThreadCredentials(String threadUser)
        throws Exception {
        NacosAuthConfigHolder holder = mock(NacosAuthConfigHolder.class);
        NacosAuthConfig config = mock(NacosAuthConfig.class);
        when(holder.getNacosAuthConfigByScope("OPEN_API")).thenReturn(config);
        when(config.isAuthEnabled()).thenReturn(true);
        when(config.getNacosAuthSystemType()).thenReturn("nacos");
        AuthPluginManager plugins = mock(AuthPluginManager.class);
        AbstractNacosAuthPluginService auth = mock(AbstractNacosAuthPluginService.class);
        when(plugins.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.of(auth));
        if (!"absent".equals(threadUser)) {
            NacosUser user = new NacosUser(threadUser, "never-copy-token");
            user.setGlobalAdmin("alice".equals(threadUser));
            IdentityContext current = new IdentityContext();
            current.setParameter(AuthConstants.NACOS_USER_KEY, user);
            RequestContextHolder.getContext().getAuthContext().setIdentityContext(current);
        }
        java.util.concurrent.atomic.AtomicBoolean granted =
            new java.util.concurrent.atomic.AtomicBoolean(true);
        when(auth.validateAuthority(any(IdentityContext.class), any(Permission.class)))
            .thenAnswer(invocation -> {
                IdentityContext context = invocation.getArgument(0);
                NacosUser user = (NacosUser) context.getParameter(AuthConstants.NACOS_USER_KEY);
                assertEquals("bob", user.getUserName());
                assertFalse(user.isGlobalAdmin());
                assertNull(user.getToken());
                AuthResult result = new AuthResult();
                result.setSuccess(granted.get());
                return result;
            });
        try (MockedStatic<NacosAuthConfigHolder> configs = mockStatic(NacosAuthConfigHolder.class);
            MockedStatic<AuthPluginManager> managers = mockStatic(AuthPluginManager.class)) {
            configs.when(NacosAuthConfigHolder::getInstance).thenReturn(holder);
            managers.when(AuthPluginManager::getInstance).thenReturn(plugins);
            DefaultVisibilityService service = new DefaultVisibilityService();
            TestResource resource =
                new TestResource("public", "private-agent", "agent", "PRIVATE", "alice");
            assertTrue(service.validateVisibility("bob", "r", "OPEN_API", resource).isAllowed());
            granted.set(false);
            assertFalse(service.validateVisibility("bob", "r", "OPEN_API", resource).isAllowed());
            assertFalse(service.validateVisibility("bob", "w", "OPEN_API", resource).isAllowed());
            assertFalse(service.validateVisibility("", "r", "OPEN_API", resource).isAllowed());
            assertFalse(service.validateVisibility(AuthConstants.ANONYMOUS_USER, "r", "OPEN_API",
                resource).isAllowed());
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"absent", "alice", "bob"})
    void foreignPluginOnlyReceivesItsMatchingAuthenticatedContext(String threadUser)
        throws Exception {
        NacosAuthConfigHolder holder = mock(NacosAuthConfigHolder.class);
        NacosAuthConfig config = mock(NacosAuthConfig.class);
        when(holder.getNacosAuthConfigByScope("OPEN_API")).thenReturn(config);
        when(config.isAuthEnabled()).thenReturn(true);
        when(config.getNacosAuthSystemType()).thenReturn("foreign");
        AuthPluginManager plugins = mock(AuthPluginManager.class);
        AuthPluginService auth = mock(AuthPluginService.class);
        when(plugins.findAuthServiceSpiImpl("foreign")).thenReturn(Optional.of(auth));
        IdentityContext context = new IdentityContext();
        if (!"absent".equals(threadUser)) {
            context.setParameter(
                com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID, threadUser);
            RequestContextHolder.getContext().getAuthContext().setIdentityContext(context);
        }
        if ("bob".equals(threadUser)) {
            when(auth.validateAuthority(org.mockito.ArgumentMatchers.same(context),
                any(Permission.class)))
                .thenReturn(AuthResult.successResult("bob"));
        }
        try (MockedStatic<NacosAuthConfigHolder> configs = mockStatic(NacosAuthConfigHolder.class);
            MockedStatic<AuthPluginManager> managers = mockStatic(AuthPluginManager.class)) {
            configs.when(NacosAuthConfigHolder::getInstance).thenReturn(holder);
            managers.when(AuthPluginManager::getInstance).thenReturn(plugins);
            TestResource resource =
                new TestResource("public", "private-agent", "agent", "PRIVATE", "owner");
            assertEquals("bob".equals(threadUser), new DefaultVisibilityService()
                .validateVisibility("bob", "r", "OPEN_API", resource).isAllowed());
            if (!"bob".equals(threadUser)) {
                org.mockito.Mockito.verifyNoInteractions(auth);
            }
            org.mockito.Mockito.verify(holder, org.mockito.Mockito.never()).getAllNacosAuthConfig();
        }
    }
    
    @Test
    void testDefaultScopeByResourceTypeAcrossApiSurfaces() {
        DefaultVisibilityService service = new DefaultVisibilityService();
        for (String apiType : new String[] {"OPEN_API", "ADMIN_API", "CONSOLE_API", ""}) {
            for (String type : new String[] {"agent", "mcp"}) {
                assertEquals("PUBLIC", service.resolveDefaultScopeForCreate("user", apiType, type));
            }
            for (String type : new String[] {"skill", "prompt", "agentspec", "unknown", ""}) {
                assertEquals("PRIVATE",
                    service.resolveDefaultScopeForCreate("user", apiType, type));
            }
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void validateVisibilityShouldAllowWhenAuthDisabled() {
        DefaultVisibilityService service = new DefaultVisibilityService();
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
        DefaultVisibilityService service = new DefaultVisibilityService();
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
        DefaultVisibilityService service = new DefaultVisibilityService();
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
        identityContext.setParameter(
            com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID, "bob");
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
        DefaultVisibilityService service = new DefaultVisibilityService();
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
        DefaultVisibilityService service = new DefaultVisibilityService();
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
        DefaultVisibilityService service = new DefaultVisibilityService();
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
        DefaultVisibilityService service = new DefaultVisibilityService();
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
    void adviseQueryShouldIncludeAuthorizedResourcesFromGrantService() {
        DefaultVisibilityService service = new DefaultVisibilityService();
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
            VisibilityGrantService grantService = mock(VisibilityGrantService.class);
            when(grantService.findAuthorizedResourceNames("userA", "public", "skill",
                VisibilityConstants.ACTION_READ)).thenReturn(List.of("skillA", "skillB"));
            ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
            when(context.getBean(VisibilityGrantService.class)).thenReturn(grantService);
            ApplicationUtils.injectContext(context);
            VisibilityQueryContext queryContext = new VisibilityQueryContext();
            queryContext.setNamespaceId("public");
            queryContext.setResourceType("skill");
            
            QueryAdvisor advisor =
                service.adviseQuery("userA", VisibilityConstants.ACTION_READ, "ADMIN_API",
                    queryContext);
            
            assertEquals(List.of("skillA", "skillB"),
                advisor.getAuthorizedPredicate().getResources());
        } finally {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
                cachedConfigMap);
            ApplicationUtils.injectContext(null);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void validateVisibilityShouldDenyWhenAuthPluginMissingOrFails() {
        DefaultVisibilityService service = new DefaultVisibilityService();
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
        DefaultVisibilityService service = new DefaultVisibilityService();
        String actual = service.resolveDefaultScopeForCreate("userA", "ADMIN_API", "skill");
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, actual);
    }
    
    @Test
    void missingApiScopeUsesEnabledPluginAndExplicitIdentity() {
        NacosAuthConfigHolder holder = mock(NacosAuthConfigHolder.class);
        NacosAuthConfig config = mock(NacosAuthConfig.class);
        when(holder.isAnyAuthEnabled()).thenReturn(true);
        when(holder.getAllNacosAuthConfig())
            .thenReturn(java.util.Collections.singletonList(config));
        when(config.isAuthEnabled()).thenReturn(true);
        when(config.getNacosAuthSystemType()).thenReturn("nacos");
        AuthPluginManager plugins = mock(AuthPluginManager.class);
        AbstractNacosAuthPluginService auth = mock(AbstractNacosAuthPluginService.class);
        when(plugins.findAuthServiceSpiImpl("nacos")).thenReturn(Optional.of(auth));
        when(auth.validateAuthority(any(IdentityContext.class), any(Permission.class)))
            .thenAnswer(invocation -> {
                IdentityContext identity = invocation.getArgument(0);
                NacosUser user = (NacosUser) identity.getParameter(AuthConstants.NACOS_USER_KEY);
                assertEquals("reader", user.getUserName());
                AuthResult result = new AuthResult();
                result.setSuccess(true);
                return result;
            });
        try (MockedStatic<NacosAuthConfigHolder> configs = mockStatic(NacosAuthConfigHolder.class);
            MockedStatic<AuthPluginManager> managers = mockStatic(AuthPluginManager.class)) {
            configs.when(NacosAuthConfigHolder::getInstance).thenReturn(holder);
            managers.when(AuthPluginManager::getInstance).thenReturn(plugins);
            assertTrue(new DefaultVisibilityService().validateVisibility("reader",
                VisibilityConstants.ACTION_READ, null, new TestResource("public", "agent", "agent",
                    VisibilityConstants.SCOPE_PRIVATE, "owner"))
                .isAllowed());
        }
    }
    
    @Test
    void getVisibilityServiceNameShouldReturnAuthPluginType() {
        assertEquals(AuthConstants.AUTH_PLUGIN_TYPE,
            new DefaultVisibilityService().getVisibilityServiceName());
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
