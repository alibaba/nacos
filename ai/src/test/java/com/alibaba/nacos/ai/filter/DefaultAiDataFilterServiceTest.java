/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.filter;

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.context.RequestContext;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AuthContext;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.plugin.datafilter.constant.DataFilterConstants;
import com.alibaba.nacos.plugin.datafilter.model.FilterableResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultAiDataFilterService} unit test.
 *
 * @author xiweng.yy
 */
class DefaultAiDataFilterServiceTest {
    
    private static final String API_TYPE_ADMIN = "ADMIN_API";
    
    private static final String API_TYPE_OPEN = "OPEN_API";
    
    private DefaultAiDataFilterService filterService;
    
    private MockedStatic<AuthPluginManager> authPluginManagerMock;
    
    private MockedStatic<RequestContextHolder> requestContextHolderMock;
    
    private AuthPluginService authPluginService;
    
    private Map<String, NacosAuthConfig> cachedConfigMap;
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        filterService = new DefaultAiDataFilterService();
        authPluginManagerMock = mockStatic(AuthPluginManager.class);
        requestContextHolderMock = mockStatic(RequestContextHolder.class);
        authPluginService = mock(AuthPluginService.class);
        
        AuthPluginManager managerInstance = mock(AuthPluginManager.class);
        authPluginManagerMock.when(AuthPluginManager::getInstance).thenReturn(managerInstance);
        when(managerInstance.findAuthServiceSpiImpl(anyString())).thenReturn(Optional.of(authPluginService));
        
        RequestContext requestContext = mock(RequestContext.class);
        AuthContext authContext = mock(AuthContext.class);
        IdentityContext identityContext = new IdentityContext();
        requestContextHolderMock.when(RequestContextHolder::getContext).thenReturn(requestContext);
        when(requestContext.getAuthContext()).thenReturn(authContext);
        when(authContext.getIdentityContext()).thenReturn(identityContext);
        
        cachedConfigMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap", new HashMap<>());
    }
    
    @AfterEach
    void tearDown() {
        authPluginManagerMock.close();
        requestContextHolderMock.close();
        if (cachedConfigMap != null) {
            ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap", cachedConfigMap);
        }
    }
    
    @Test
    void testGetFilterServiceName() {
        assertEquals("nacos-default-ai", filterService.getFilterServiceName());
    }
    
    @Test
    void testAuthDisabledForApiTypeReturnsAll() {
        setAuthConfig(API_TYPE_ADMIN, false);
        
        List<TestResource> candidates = Arrays.asList(privateResource("alice"), privateResource("bob"));
        List<TestResource> result = filterService.filter("charlie", DataFilterConstants.ACTION_READ, API_TYPE_ADMIN,
                candidates);
        assertEquals(2, result.size());
    }
    
    @Test
    void testAuthEnabledForAdminButDisabledForOpenApi() {
        setAuthConfig(API_TYPE_ADMIN, true);
        setAuthConfig(API_TYPE_OPEN, false);
        denyAllPermissions();
        
        TestResource resource = privateResource("alice");
        List<TestResource> adminResult = filterService.filter("bob", DataFilterConstants.ACTION_READ, API_TYPE_ADMIN,
                Collections.singletonList(resource));
        assertTrue(adminResult.isEmpty());
        
        List<TestResource> openResult = filterService.filter("bob", DataFilterConstants.ACTION_READ, API_TYPE_OPEN,
                Collections.singletonList(resource));
        assertEquals(1, openResult.size());
    }
    
    @Test
    void testBlankApiTypeFallsBackToAnyAuthEnabled() {
        setAuthConfig(API_TYPE_ADMIN, true);
        denyAllPermissions();
        
        TestResource resource = privateResource("alice");
        List<TestResource> result = filterService.filter("bob", DataFilterConstants.ACTION_READ, null,
                Collections.singletonList(resource));
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testBlankApiTypeNoAuthEnabled() {
        setAuthConfig(API_TYPE_ADMIN, false);
        setAuthConfig(API_TYPE_OPEN, false);
        
        TestResource resource = privateResource("alice");
        List<TestResource> result = filterService.filter("bob", DataFilterConstants.ACTION_READ, null,
                Collections.singletonList(resource));
        assertEquals(1, result.size());
    }
    
    @Test
    void testOwnerPassesReadAndWrite() {
        enableAdminAuth();
        denyAllPermissions();
        
        TestResource resource = privateResource("alice");
        List<TestResource> readResult = filterService.filter("alice", DataFilterConstants.ACTION_READ, API_TYPE_ADMIN,
                Collections.singletonList(resource));
        assertEquals(1, readResult.size());
        
        List<TestResource> writeResult = filterService.filter("alice", DataFilterConstants.ACTION_WRITE, API_TYPE_ADMIN,
                Collections.singletonList(resource));
        assertEquals(1, writeResult.size());
    }
    
    @Test
    void testPublicScopePassesRead() {
        enableAdminAuth();
        denyAllPermissions();
        
        TestResource resource = publicResource("alice");
        List<TestResource> result = filterService.filter("bob", DataFilterConstants.ACTION_READ, API_TYPE_ADMIN,
                Collections.singletonList(resource));
        assertEquals(1, result.size());
    }
    
    @Test
    void testPublicScopeDoesNotPassWrite() {
        enableAdminAuth();
        denyAllPermissions();
        
        TestResource resource = publicResource("alice");
        List<TestResource> result = filterService.filter("bob", DataFilterConstants.ACTION_WRITE, API_TYPE_ADMIN,
                Collections.singletonList(resource));
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testPrivateReadDeniedWithoutPermission() {
        enableAdminAuth();
        denyAllPermissions();
        
        TestResource resource = privateResource("alice");
        List<TestResource> result = filterService.filter("bob", DataFilterConstants.ACTION_READ, API_TYPE_ADMIN,
                Collections.singletonList(resource));
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testExplicitPermissionGrantsAccess() throws Exception {
        enableAdminAuth();
        AuthResult success = new AuthResult();
        success.setSuccess(true);
        when(authPluginService.validateAuthority(any(IdentityContext.class), any(Permission.class))).thenReturn(
                success);
        
        TestResource resource = privateResource("alice");
        List<TestResource> result = filterService.filter("bob", DataFilterConstants.ACTION_READ, API_TYPE_ADMIN,
                Collections.singletonList(resource));
        assertEquals(1, result.size());
    }
    
    @Test
    void testMixedCandidatesFiltering() {
        enableAdminAuth();
        denyAllPermissions();
        
        TestResource publicAlice = publicResource("alice");
        TestResource privateAlice = privateResource("alice");
        TestResource privateBob = privateResource("bob");
        
        List<TestResource> candidates = Arrays.asList(publicAlice, privateAlice, privateBob);
        List<TestResource> result = filterService.filter("bob", DataFilterConstants.ACTION_READ, API_TYPE_ADMIN,
                candidates);
        assertEquals(2, result.size());
        assertTrue(result.contains(publicAlice));
        assertTrue(result.contains(privateBob));
    }
    
    @Test
    void testEmptyNamespaceDefaultsToPublic() {
        enableAdminAuth();
        denyAllPermissions();
        
        TestResource resource = new TestResource("", "my-skill", "skill", DataFilterConstants.SCOPE_PRIVATE, "alice");
        List<TestResource> result = filterService.filter("bob", DataFilterConstants.ACTION_READ, API_TYPE_ADMIN,
                Collections.singletonList(resource));
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testUnknownApiTypeTreatedAsDisabled() {
        setAuthConfig(API_TYPE_ADMIN, true);
        
        TestResource resource = privateResource("alice");
        List<TestResource> result = filterService.filter("bob", DataFilterConstants.ACTION_READ, "UNKNOWN_API",
                Collections.singletonList(resource));
        assertEquals(1, result.size());
    }
    
    @SuppressWarnings("unchecked")
    private void setAuthConfig(String scope, boolean enabled) {
        Map<String, NacosAuthConfig> configMap = (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        NacosAuthConfig mockConfig = mock(NacosAuthConfig.class);
        when(mockConfig.getAuthScope()).thenReturn(scope);
        when(mockConfig.isAuthEnabled()).thenReturn(enabled);
        when(mockConfig.getNacosAuthSystemType()).thenReturn("nacos");
        configMap.put(scope, mockConfig);
    }
    
    private void enableAdminAuth() {
        setAuthConfig(API_TYPE_ADMIN, true);
    }
    
    private void denyAllPermissions() {
        try {
            AuthResult denied = new AuthResult();
            denied.setSuccess(false);
            when(authPluginService.validateAuthority(any(IdentityContext.class), any(Permission.class))).thenReturn(
                    denied);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private TestResource publicResource(String owner) {
        return new TestResource("public", "test-skill", "skill", DataFilterConstants.SCOPE_PUBLIC, owner);
    }
    
    private TestResource privateResource(String owner) {
        return new TestResource("public", "test-skill", "skill", DataFilterConstants.SCOPE_PRIVATE, owner);
    }
    
    static class TestResource extends FilterableResource {
        
        private final String namespaceId;
        
        private final String resourceName;
        
        private final String resourceType;
        
        TestResource(String namespaceId, String resourceName, String resourceType, String scope, String owner) {
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
