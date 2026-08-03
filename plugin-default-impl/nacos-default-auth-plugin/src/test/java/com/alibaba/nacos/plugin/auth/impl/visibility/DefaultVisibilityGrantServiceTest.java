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
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityResourceLocator;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultVisibilityGrantServiceTest {
    
    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.core.auth.system.type", "nacos");
        environment.setProperty("nacos.core.auth.server.identity.key", "nacos");
        environment.setProperty("nacos.core.auth.server.identity.value", "nacos");
        environment.setProperty("nacos.core.auth.admin.enabled", "true");
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void buildUserRoleNameShouldBeReservedDeterministicAndBounded() {
        String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
        
        assertEquals(roleName, VisibilityGrantRoleHelper.buildUserRoleName("bob"));
        assertTrue(roleName.startsWith(VisibilityGrantRoleHelper.buildUserRoleNamePrefix()));
        assertTrue(roleName.length() <= 50);
    }
    
    @Test
    void buildUserRoleNameShouldCreateDifferentRolesForDifferentUsers() {
        String bobRole = VisibilityGrantRoleHelper.buildUserRoleName("bob");
        String aliceRole = VisibilityGrantRoleHelper.buildUserRoleName("alice");
        
        assertNotEquals(bobRole, aliceRole);
        assertTrue(bobRole.length() <= 50);
        assertTrue(aliceRole.length() <= 50);
    }
    
    @Test
    void helperShouldNormalizeAndParseVisibilityResourceIdentifier() {
        String resourceId =
            VisibilityGrantRoleHelper.buildResourceIdentifier("", " Skill ", "Demo/Skill");
        
        VisibilityGrantRoleHelper.ParsedGrantResource parsed =
            VisibilityGrantRoleHelper.tryParseResourceIdentifier(resourceId);
        
        assertEquals("@@visibility/public/skill/Demo/Skill", resourceId);
        assertEquals("public", parsed.getNamespaceId());
        assertEquals("skill", parsed.getResourceType());
        assertEquals("Demo/Skill", parsed.getResourceName());
    }
    
    @Test
    void helperShouldRejectInvalidVisibilityResourceIdentifier() {
        assertTrue(VisibilityGrantRoleHelper.tryParseResourceIdentifier(null) == null);
        assertTrue(VisibilityGrantRoleHelper.tryParseResourceIdentifier("plain-resource") == null);
        assertTrue(VisibilityGrantRoleHelper
            .tryParseResourceIdentifier("@@visibility//skill/demo") == null);
        assertTrue(VisibilityGrantRoleHelper.isUserGrantRole(
            VisibilityGrantRoleHelper.buildUserRoleName("bob")));
        assertTrue(!VisibilityGrantRoleHelper.isUserGrantRole("shared-role"));
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
        EnvUtil.setEnvironment(null);
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
            String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
            String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier("public",
                "skill", "demo-skill");
            when(roleService.isDuplicatePermission(roleName, resourceId, "r"))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false));
            when(roleService.getRoles("bob")).thenReturn(List.of());
            when(roleService.isDuplicatePermission(roleName, resourceId, "rw"))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false));
            
            service.grant("public", "skill", "demo-skill", "bob", "w");
            
            verify(roleService).addRole(roleName, "bob");
            verify(roleService).addPermission(roleName, resourceId, "rw");
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
    @SuppressWarnings("unchecked")
    void grantShouldAllowGlobalAdminToManageGrant() throws Exception {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        when(userService.getUser("bob")).thenReturn(new User());
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("admin", true);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
            String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier("public",
                "skill", "demo-skill");
            when(roleService.getRoles("bob")).thenReturn(List.of());
            when(roleService.isDuplicatePermission(roleName, resourceId, "r"))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false));
            
            service.grant("public", "skill", "demo-skill", "bob", "r");
            
            verify(roleService).addPermission(roleName, resourceId, "r");
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @Test
    void grantShouldRejectWhenVisibilityLocatorIsUnavailable() {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockMissingLocator();
        
        assertThrows(NacosApiException.class,
            () -> service.grant("public", "skill", "demo-skill", "bob", "r"));
        verify(roleService, never()).addRole(anyString(), anyString());
    }
    
    @Test
    void grantShouldRejectWhenManagedResourceDoesNotExist() {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new EmptyLocator());
        
        assertThrows(NacosApiException.class,
            () -> service.grant("public", "skill", "missing-skill", "bob", "r"));
        verify(roleService, never()).addRole(anyString(), anyString());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void grantShouldReuseDedicatedUserRoleForMultipleResources() throws Exception {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        when(userService.getUser("bob")).thenReturn(new User());
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
            RoleInfo existingRole = new RoleInfo();
            existingRole.setRole(roleName);
            when(roleService.getRoles("bob")).thenReturn(List.of(), List.of(existingRole));
            when(roleService.isDuplicatePermission(roleName,
                VisibilityGrantRoleHelper.buildResourceIdentifier("public", "skill",
                    "skill-a"),
                "r")).thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false));
            when(roleService.isDuplicatePermission(roleName,
                VisibilityGrantRoleHelper.buildResourceIdentifier("public", "skill",
                    "skill-b"),
                "r")).thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false));
            when(roleService.isDuplicatePermission(roleName,
                VisibilityGrantRoleHelper.buildResourceIdentifier("public", "skill",
                    "skill-b"),
                "rw")).thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false));
            
            service.grant("public", "skill", "skill-a", "bob", "r");
            service.grant("public", "skill", "skill-b", "bob", "w");
            
            verify(roleService, times(1)).addRole(roleName, "bob");
            verify(roleService).addPermission(roleName,
                VisibilityGrantRoleHelper.buildResourceIdentifier("public", "skill",
                    "skill-a"),
                "r");
            verify(roleService).addPermission(roleName,
                VisibilityGrantRoleHelper.buildResourceIdentifier("public", "skill",
                    "skill-b"),
                "rw");
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void repeatedGrantShouldReuseRoleAndSkipDuplicatePermission() throws Exception {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        when(userService.getUser("bob")).thenReturn(new User());
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
            String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier("public",
                "skill", "demo-skill");
            RoleInfo existingRole = new RoleInfo();
            existingRole.setRole(roleName);
            when(roleService.getRoles("bob")).thenReturn(List.of(), List.of(existingRole));
            when(roleService.isDuplicatePermission(roleName, resourceId, "r"))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(true));
            
            service.grant("public", "skill", "demo-skill", "bob", "r");
            service.grant("public", "skill", "demo-skill", "bob", "r");
            
            verify(roleService, times(1)).addRole(roleName, "bob");
            verify(roleService, times(1)).addPermission(roleName, resourceId, "r");
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void grantWriteShouldReplaceExistingReadPermission() throws Exception {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        when(userService.getUser("bob")).thenReturn(new User());
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
            String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier("public",
                "skill", "demo-skill");
            RoleInfo existingRole = new RoleInfo();
            existingRole.setRole(roleName);
            when(roleService.getRoles("bob")).thenReturn(List.of(existingRole));
            when(roleService.isDuplicatePermission(roleName, resourceId, "r"))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(true));
            when(roleService.isDuplicatePermission(roleName, resourceId, "rw"))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false));
            
            service.grant("public", "skill", "demo-skill", "bob", "w");
            
            verify(roleService).deletePermission(roleName, resourceId, "r");
            verify(roleService).addPermission(roleName, resourceId, "rw");
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void grantShouldRollbackRoleBindingWhenPermissionCreationFails() {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        when(userService.getUser("bob")).thenReturn(new User());
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
            String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier("public",
                "skill", "demo-skill");
            when(roleService.getRoles("bob")).thenReturn(List.of());
            when(roleService.isDuplicatePermission(roleName, resourceId, "r"))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false));
            doThrow(new IllegalStateException("permission failed")).when(roleService)
                .addPermission(roleName, resourceId, "r");
            
            assertThrows(IllegalStateException.class,
                () -> service.grant("public", "skill", "demo-skill", "bob", "r"));
            
            verify(roleService).deleteRole(roleName, "bob");
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
        String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
        RoleInfo userRole = new RoleInfo();
        userRole.setRole(roleName);
        PermissionInfo readPermission = new PermissionInfo();
        readPermission.setRole(roleName);
        readPermission.setResource(VisibilityGrantRoleHelper.buildResourceIdentifier("public",
            "skill", "skill-a"));
        readPermission.setAction(VisibilityConstants.ACTION_READ);
        PermissionInfo writePermission = new PermissionInfo();
        writePermission.setRole(roleName);
        writePermission.setResource(VisibilityGrantRoleHelper.buildResourceIdentifier("public",
            "skill", "skill-b"));
        writePermission.setAction("rw");
        when(roleService.getRoles("bob")).thenReturn(List.of(userRole));
        when(roleService.getPermissions(roleName)).thenReturn(List.of(readPermission,
            writePermission));
        
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
    void findAuthorizedResourceNamesShouldIgnoreOrphanRoleWithoutPermissions() {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
        RoleInfo userRole = new RoleInfo();
        userRole.setRole(roleName);
        when(roleService.getRoles("bob")).thenReturn(List.of(userRole));
        when(roleService.getPermissions(roleName)).thenReturn(List.of());
        
        List<String> readable =
            service.findAuthorizedResourceNames("bob", "public", "skill",
                VisibilityConstants.ACTION_READ);
        
        assertTrue(readable.isEmpty());
    }
    
    @Test
    void findAuthorizedResourceNamesShouldUseOnlyDedicatedRolePermissions() {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        String dedicatedRoleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
        RoleInfo dedicatedRole = new RoleInfo();
        dedicatedRole.setRole(dedicatedRoleName);
        RoleInfo sharedRole = new RoleInfo();
        sharedRole.setRole("shared-role");
        PermissionInfo dedicatedPermission = new PermissionInfo();
        dedicatedPermission.setRole(dedicatedRoleName);
        dedicatedPermission.setResource(VisibilityGrantRoleHelper.buildResourceIdentifier("public",
            "skill", "skill-owned"));
        dedicatedPermission.setAction(VisibilityConstants.ACTION_READ);
        when(roleService.getRoles("bob")).thenReturn(List.of(sharedRole, dedicatedRole));
        when(roleService.getPermissions(dedicatedRoleName))
            .thenReturn(List.of(dedicatedPermission));
        
        List<String> readable =
            service.findAuthorizedResourceNames("bob", "public", "skill",
                VisibilityConstants.ACTION_READ);
        
        assertEquals(List.of("skill-owned"), readable);
        verify(roleService, never()).getPermissions("shared-role");
    }
    
    @Test
    void findAuthorizedResourceNamesShouldIgnoreInvalidAndMismatchedPermissions() {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
        RoleInfo userRole = new RoleInfo();
        userRole.setRole(roleName);
        PermissionInfo invalidPermission = new PermissionInfo();
        invalidPermission.setResource("plain-resource");
        invalidPermission.setAction(VisibilityConstants.ACTION_READ);
        PermissionInfo otherNamespacePermission = new PermissionInfo();
        otherNamespacePermission.setResource(VisibilityGrantRoleHelper.buildResourceIdentifier(
            "other", "skill", "skill-a"));
        otherNamespacePermission.setAction(VisibilityConstants.ACTION_READ);
        PermissionInfo otherTypePermission = new PermissionInfo();
        otherTypePermission.setResource(VisibilityGrantRoleHelper.buildResourceIdentifier("public",
            "prompt", "prompt-a"));
        otherTypePermission.setAction(VisibilityConstants.ACTION_READ);
        PermissionInfo matchedPermission = new PermissionInfo();
        matchedPermission.setResource(VisibilityGrantRoleHelper.buildResourceIdentifier("public",
            "skill", "skill-b"));
        matchedPermission.setAction(VisibilityConstants.ACTION_READ);
        when(roleService.getRoles("bob")).thenReturn(List.of(userRole));
        when(roleService.getPermissions(roleName)).thenReturn(List.of(invalidPermission,
            otherNamespacePermission, otherTypePermission, matchedPermission));
        
        List<String> readable =
            service.findAuthorizedResourceNames("bob", "public", "skill",
                VisibilityConstants.ACTION_READ);
        
        assertEquals(List.of("skill-b"), readable);
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
    void revokeShouldDeleteExistingVisibilityPermission() throws Exception {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        when(userService.getUser("bob")).thenReturn(new User());
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
            String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier("public",
                "skill", "demo-skill");
            when(roleService.isDuplicatePermission(roleName, resourceId, "rw"))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(true));
            
            service.revoke("public", "skill", "demo-skill", "bob", "w");
            
            verify(roleService, never()).deleteRole(roleName, "bob");
            verify(roleService, times(1)).deletePermission(roleName, resourceId, "rw");
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void revokeShouldRejectWhenVisibilityPermissionDoesNotExist() {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        when(userService.getUser("bob")).thenReturn(new User());
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        mockLocator(new TestLocator(new TestResource("public", "skill", "demo-skill", "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
            String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier("public",
                "skill", "demo-skill");
            when(roleService.isDuplicatePermission(roleName, resourceId, "rw"))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false));
            
            assertThrows(NacosApiException.class,
                () -> service.revoke("public", "skill", "demo-skill", "bob", "rw"));
            
            verify(roleService, never()).deletePermission(roleName, resourceId, "rw");
        } finally {
            restoreAuthConfig(cached);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void grantAndRevokeShouldSupportMaximumCanonicalResourceLength() throws Exception {
        NacosRoleService roleService = mock(NacosRoleService.class);
        NacosUserService userService = mock(NacosUserService.class);
        when(userService.getUser("bob")).thenReturn(new User());
        DefaultVisibilityGrantService service =
            new DefaultVisibilityGrantService(roleService, userService);
        String prefix = VisibilityGrantRoleHelper.buildResourceIdentifier("public", "skill", "");
        String resourceName = "a".repeat(512 - prefix.length());
        String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier("public", "skill",
            resourceName);
        assertEquals(512, resourceId.length());
        mockLocator(new TestLocator(new TestResource("public", "skill", resourceName, "alice")));
        setCurrentUser("alice", false);
        Map<String, NacosAuthConfig> cached = authEnabledConfig();
        try {
            String roleName = VisibilityGrantRoleHelper.buildUserRoleName("bob");
            when(roleService.getRoles("bob")).thenReturn(List.of());
            when(roleService.isDuplicatePermission(roleName, resourceId, "r"))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(false))
                .thenReturn(com.alibaba.nacos.api.model.v2.Result.success(true));
            
            service.grant("public", "skill", resourceName, "bob", "r");
            service.revoke("public", "skill", resourceName, "bob", "r");
            
            verify(roleService).addPermission(roleName, resourceId, "r");
            verify(roleService).deletePermission(roleName, resourceId, "r");
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
    
    private void mockMissingLocator() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getBean(VisibilityResourceLocator.class))
            .thenThrow(new NoSuchBeanDefinitionException(VisibilityResourceLocator.class));
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
    
    private static class EmptyLocator implements VisibilityResourceLocator {
        
        @Override
        public Optional<VisibilityResource> findResource(String namespaceId, String resourceType,
            String resourceName) {
            return Optional.empty();
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
