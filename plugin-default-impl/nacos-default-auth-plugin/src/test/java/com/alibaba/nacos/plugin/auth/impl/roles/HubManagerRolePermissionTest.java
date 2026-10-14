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

package com.alibaba.nacos.plugin.auth.impl.roles;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.impl.NacosAuthPluginService;
import com.alibaba.nacos.plugin.auth.impl.authenticate.DefaultAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfigProvider;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercise the intrinsic Hub governance role through the default RBAC and auth plugin.
 */
@ExtendWith(MockitoExtension.class)
class HubManagerRolePermissionTest {
    
    private static final String USERNAME = "hub-reader";
    
    @Mock
    private NacosAuthPluginConfigProvider configProvider;
    
    @Mock
    private NacosAuthPluginConfig config;
    
    @Mock
    private RolePersistService rolePersistService;
    
    @Mock
    private PermissionPersistService permissionPersistService;
    
    @Mock
    private NacosUserService userService;
    
    private NacosRoleServiceDirectImpl roleService;
    
    private NacosUser user;
    
    @BeforeEach
    void setUp() {
        when(configProvider.getConfig()).thenReturn(config);
        roleService =
            new NacosRoleServiceDirectImpl(configProvider, rolePersistService, userService,
                permissionPersistService);
        user = new NacosUser(USERNAME);
    }
    
    @Test
    void roleBindingAloneGrantsGovernanceWriteWithoutGlobalAdmin() {
        bind(AiConstants.Hub.MANAGER_ROLE);
        assertTrue(roleService.hasPermission(user, governance("w")));
        assertFalse(user.isGlobalAdmin());
        assertFalse(roleService.hasGlobalAdminRole(USERNAME));
        verifyNoInteractions(permissionPersistService);
    }
    
    @Test
    void intrinsicPermissionDoesNotGrantReadOrCombinedActions() {
        bind(AiConstants.Hub.MANAGER_ROLE);
        assertFalse(roleService.hasPermission(user, governance("r")));
        assertFalse(roleService.hasPermission(user, governance("rw")));
    }
    
    @Test
    void intrinsicPermissionRequiresSpecifiedResourceType() {
        bind(AiConstants.Hub.MANAGER_ROLE);
        assertFalse(
            roleService.hasPermission(user, permission("public", AiConstants.Hub.MANAGE_RESOURCE,
                SignType.AI, "w")));
        assertFalse(roleService.hasPermission(user, permission("", AiConstants.Hub.MANAGE_RESOURCE,
            SignType.CONSOLE, "w")));
    }
    
    @Test
    void roleDoesNotGrantSourceNamespaceOrConsoleAdministration() {
        bind(AiConstants.Hub.MANAGER_ROLE);
        for (String name : new String[] {"console/roles", "console/users", "ai-hub/entry",
            "ai-hub/manage-other"}) {
            assertFalse(
                roleService.hasPermission(user, permission("", name, SignType.SPECIFIED, "w")),
                name);
        }
        assertFalse(
            roleService.hasPermission(user, permission("hub-test-source-a", "skill/find-skills",
                SignType.AI, "w")));
        assertFalse(user.isGlobalAdmin());
    }
    
    @Test
    void ordinaryAndSimilarlyNamedRolesDoNotGrantGovernance() {
        for (String role : new String[] {"hub-reader", "hub_manager", "HUB_MANAGER_OTHER"}) {
            bind(role);
            assertFalse(roleService.hasPermission(user, governance("w")), role);
        }
    }
    
    @Test
    void unboundAndAnonymousUsersDoNotGetGovernancePermission() {
        assertFalse(roleService.hasPermission(user, governance("w")));
        assertFalse(roleService.hasPermission(new NacosUser(AuthConstants.ANONYMOUS_ROLE),
            governance("w")));
    }
    
    @Test
    void sourceWritePermissionDoesNotGrantGovernance() {
        bind("source-owner");
        grant("source-owner", "hub-test-source-a:*:*", "rw");
        assertTrue(
            roleService.hasPermission(user, permission("hub-test-source-a", "skill/find-skills",
                SignType.AI, "w")));
        assertFalse(roleService.hasPermission(user, governance("w")));
    }
    
    @Test
    void explicitGovernanceGrantsForOtherRolesRemainSupported() {
        bind("custom-manager");
        grant("custom-manager", AiConstants.Hub.MANAGE_RESOURCE, "w");
        assertTrue(roleService.hasPermission(user, governance("w")));
        assertFalse(user.isGlobalAdmin());
    }
    
    @Test
    void globalAdministratorKeepsExistingPermissions() {
        bind(AuthConstants.GLOBAL_ADMIN_ROLE);
        assertTrue(roleService.hasPermission(user, governance("w")));
        assertTrue(roleService.hasPermission(user,
            permission("", "console/roles", SignType.SPECIFIED, "w")));
        assertTrue(user.isGlobalAdmin());
    }
    
    @Test
    void addingAndRemovingBindingInvalidatesCachedRolesWithoutPermissionWrites() {
        when(config.isCachingEnabled()).thenReturn(true);
        List<RoleInfo> bindings = new ArrayList<>();
        when(rolePersistService.getRolesByUserNameAndRoleName(USERNAME, "", 1, Integer.MAX_VALUE))
            .thenAnswer(invocation -> page(new ArrayList<>(bindings)));
        when(userService.getUser(USERNAME)).thenReturn(new User());
        doAnswer(invocation -> {
            bindings.add(binding(AiConstants.Hub.MANAGER_ROLE));
            return null;
        }).when(rolePersistService).addRole(AiConstants.Hub.MANAGER_ROLE, USERNAME);
        doAnswer(invocation -> {
            bindings.clear();
            return null;
        }).when(rolePersistService).deleteRole(AiConstants.Hub.MANAGER_ROLE, USERNAME);
        
        assertFalse(roleService.hasPermission(user, governance("w")));
        roleService.addRole(AiConstants.Hub.MANAGER_ROLE, USERNAME);
        assertTrue(roleService.hasPermission(user, governance("w")));
        assertTrue(roleService.hasPermission(user, governance("w")));
        roleService.deleteRole(AiConstants.Hub.MANAGER_ROLE, USERNAME);
        assertFalse(roleService.hasPermission(user, governance("w")));
        verifyNoInteractions(permissionPersistService);
    }
    
    @Test
    void actualAuthPluginAuthorizesBoundManagerAndRejectsAfterRevocation() {
        bind(AiConstants.Hub.MANAGER_ROLE);
        DefaultAuthenticationManager manager = new DefaultAuthenticationManager(userService,
            mock(TokenManagerDelegate.class), roleService);
        NacosAuthPluginService plugin = new NacosAuthPluginService();
        ReflectionTestUtils.setField(plugin, "authenticationManager", manager);
        IdentityContext identity = new IdentityContext();
        identity.setParameter(AuthConstants.NACOS_USER_KEY, user);
        
        assertTrue(plugin.validateAuthority(identity, governance("w")).isSuccess());
        AuthResult<?> consoleDenied = plugin.validateAuthority(identity,
            permission("", "console/roles", SignType.SPECIFIED, "w"));
        assertFalse(consoleDenied.isSuccess());
        assertEquals(403, consoleDenied.getErrorCode());
        assertFalse(user.isGlobalAdmin());
        when(rolePersistService.getRolesByUserNameAndRoleName(USERNAME, "", 1, Integer.MAX_VALUE))
            .thenReturn(page(Collections.emptyList()));
        AuthResult<?> revoked = plugin.validateAuthority(identity, governance("w"));
        assertFalse(revoked.isSuccess());
        assertEquals(403, revoked.getErrorCode());
        verifyNoInteractions(permissionPersistService);
    }
    
    @Test
    void remoteConsoleUsesTheSameIntrinsicRoleAndRevocationRules() {
        bind(AiConstants.Hub.MANAGER_ROLE);
        assertTrue(roleService.hasPermission(user, governance("w")));
        NacosRoleServiceRemoteImpl remote = new NacosRoleServiceRemoteImpl();
        Map<String, List<RoleInfo>> roles = new HashMap<>();
        roles.put(USERNAME, Collections.singletonList(binding(AiConstants.Hub.MANAGER_ROLE)));
        Map<String, List<PermissionInfo>> permissions = new HashMap<>();
        permissions.put(AiConstants.Hub.MANAGER_ROLE, Collections.emptyList());
        ReflectionTestUtils.setField(remote, "roleInfoMap", roles);
        ReflectionTestUtils.setField(remote, "permissionInfoMap", permissions);
        
        assertTrue(remote.hasPermission(user, governance("w")));
        assertFalse(remote.hasPermission(user,
            permission("public", "skill/find-skills", SignType.AI, "w")));
        assertFalse(
            remote.hasPermission(user, permission("", "console/users", SignType.SPECIFIED, "w")));
        assertFalse(remote.hasGlobalAdminRole(USERNAME));
        roles.put(USERNAME, Collections.emptyList());
        assertFalse(remote.hasPermission(user, governance("w")));
        verifyNoInteractions(permissionPersistService);
    }
    
    private void bind(String role) {
        when(rolePersistService.getRolesByUserNameAndRoleName(USERNAME, "", 1, Integer.MAX_VALUE))
            .thenReturn(page(Collections.singletonList(binding(role))));
    }
    
    private RoleInfo binding(String role) {
        RoleInfo info = new RoleInfo();
        info.setUsername(USERNAME);
        info.setRole(role);
        return info;
    }
    
    private void grant(String role, String resource, String action) {
        PermissionInfo info = new PermissionInfo();
        info.setRole(role);
        info.setResource(resource);
        info.setAction(action);
        when(permissionPersistService.getPermissions(role, 1, Integer.MAX_VALUE))
            .thenReturn(page(Collections.singletonList(info)));
    }
    
    private <T> Page<T> page(List<T> items) {
        Page<T> page = new Page<>();
        page.setPageItems(items);
        page.setTotalCount(items.size());
        return page;
    }
    
    private Permission governance(String action) {
        return permission("", AiConstants.Hub.MANAGE_RESOURCE, SignType.SPECIFIED, action);
    }
    
    private Permission permission(String namespace, String name, String type, String action) {
        return new Permission(new Resource(namespace, "", name, type, new Properties()), action);
    }
}
