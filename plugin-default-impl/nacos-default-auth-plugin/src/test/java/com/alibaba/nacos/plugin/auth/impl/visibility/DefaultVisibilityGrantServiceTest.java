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

package com.alibaba.nacos.plugin.auth.impl.visibility;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityResourceLocator;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultVisibilityGrantServiceTest {
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
        ApplicationUtils.injectContext(null);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void grantShouldAddRoleAndPermissionForOwner() throws Exception {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        when(userService.getUser("bob")).thenReturn(new User());
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            when(roleService.getRoles("bob")).thenReturn(List.of());
            when(roleService.isDuplicatePermission(
                VisibilityGrantRoleHelper.buildRoleName("public", "skill", "demo-skill", "w"),
                VisibilityGrantRoleHelper.buildResourceIdentifier("public", "skill",
                    "demo-skill"),
                "rw")).thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false));
            
            service.grant("public", "skill", "demo-skill", "bob", "w");
            
            verify(roleService).addRole(
                VisibilityGrantRoleHelper.buildRoleName("public", "skill", "demo-skill", "w"),
                "bob");
            verify(roleService).addPermission(
                VisibilityGrantRoleHelper.buildRoleName("public", "skill", "demo-skill", "w"),
                VisibilityGrantRoleHelper.buildResourceIdentifier("public", "skill",
                    "demo-skill"),
                "rw");
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void grantShouldDenyForNonOwnerNonAdmin() {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("carol", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            assertThrows(NacosApiException.class,
                () -> service.grant("public", "skill", "demo-skill", "bob", "r"));
            verify(roleService, never()).addRole(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @Test
    void findAuthorizedResourceNamesShouldIncludeReadAndWriteGrantsForReadQueries() {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        RoleInfo readRole = new RoleInfo();
        readRole.setRole(VisibilityGrantRoleHelper.buildRoleName("public", "skill", "skill-a",
            VisibilityConstants.ACTION_READ));
        RoleInfo writeRole = new RoleInfo();
        writeRole.setRole(VisibilityGrantRoleHelper.buildRoleName("public", "skill", "skill-b",
            VisibilityConstants.ACTION_WRITE));
        when(roleService.getRoles("bob")).thenReturn(List.of(readRole, writeRole));
        
        List<String> readable =
            service.findAuthorizedResourceNames("bob", "public", "skill",
                VisibilityConstants.ACTION_READ);
        List<String> writable =
            service.findAuthorizedResourceNames("bob", "public", "skill",
                VisibilityConstants.ACTION_WRITE);
        
        assertEquals(List.of("skill-a", "skill-b"), readable);
        assertEquals(List.of("skill-b"), writable);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void listShouldReturnParsedGrantInfo() throws Exception {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            RoleInfo roleInfo = new RoleInfo();
            roleInfo.setRole(VisibilityGrantRoleHelper.buildRoleName("public", "skill",
                "demo-skill", "rw"));
            roleInfo.setUsername("bob");
            com.alibaba.nacos.api.model.Page<RoleInfo> page =
                new com.alibaba.nacos.api.model.Page<>();
            page.setPageItems(List.of(roleInfo));
            when(roleService.findRoles("", VisibilityGrantRoleHelper.buildRolePrefix("public",
                "skill", "demo-skill"), 1, Integer.MAX_VALUE)).thenReturn(page);
            
            List<VisibilityGrantInfo> grants = service.list("public", "skill", "demo-skill");
            
            assertEquals(1, grants.size());
            assertEquals("bob", grants.get(0).getUsername());
            assertEquals("rw", grants.get(0).getAction());
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void grantShouldRejectUnsupportedAction() {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        when(userService.getUser("bob")).thenReturn(new User());
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            assertThrows(NacosApiException.class,
                () -> service.grant("public", "skill", "demo-skill", "bob", "x"));
            verify(roleService, never()).addRole(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void revokeShouldNotRequireExistingGrantee() throws Exception {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            String roleName =
                VisibilityGrantRoleHelper.buildRoleName("public", "skill", "demo-skill", "w");
            when(roleService.getRoles("", roleName, 1, 1))
                .thenReturn(new com.alibaba.nacos.api.model.Page<>());
            
            service.revoke("public", "skill", "demo-skill", "bob", "w");
            
            verify(userService, never()).getUser("bob");
            verify(roleService).deleteRole(roleName, "bob");
            verify(roleService, times(1)).deletePermission(roleName,
                VisibilityGrantRoleHelper.buildResourceIdentifier("public", "skill",
                    "demo-skill"),
                "rw");
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, NacosAuthConfig> authEnabledConfig() {
        Map<String, NacosAuthConfig> cached =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
        when(authConfig.isAuthEnabled()).thenReturn(true);
        Map<String, NacosAuthConfig> map = new HashMap<>();
        map.put("ADMIN_API", authConfig);
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            map);
        return cached;
    }
    
    private void restoreAuthConfig(Map<String, NacosAuthConfig> cached) {
        ReflectionTestUtils.setField(NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap",
            cached);
    }
    
    private void setCurrentUser(String username, boolean globalAdmin) {
        IdentityContext identityContext = new IdentityContext();
        NacosUser user = new NacosUser(username);
        user.setGlobalAdmin(globalAdmin);
        identityContext.setParameter(AuthConstants.NACOS_USER_KEY, user);
        RequestContextHolder.getContext().getAuthContext().setIdentityContext(identityContext);
    }
    
    private void mockLocator(VisibilityResourceLocator locator) {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getBean(VisibilityResourceLocator.class)).thenReturn(locator);
        ApplicationUtils.injectContext(context);
    }
    
    private static class TestLocator implements VisibilityResourceLocator {
        
        private final VisibilityResource resource;
        
        private TestLocator(VisibilityResource resource) {
            this.resource = resource;
        }
        
        @Override
        public Optional<VisibilityResource> findResource(String namespaceId, String resourceType,
            String resourceName) {
            return Optional.of(resource);
        }
    }
    
    private static class TestResource extends VisibilityResource {
        
        private final String namespaceId;
        
        private final String resourceType;
        
        private final String resourceName;
        
        private TestResource(String namespaceId, String resourceType, String resourceName,
            String owner) {
            this.namespaceId = namespaceId;
            this.resourceType = resourceType;
            this.resourceName = resourceName;
            setOwner(owner);
            setScope(VisibilityConstants.SCOPE_PRIVATE);
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
