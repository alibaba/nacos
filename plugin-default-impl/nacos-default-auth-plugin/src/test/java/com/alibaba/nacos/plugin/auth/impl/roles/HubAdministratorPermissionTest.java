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
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfigProvider;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Hub governance reuses the existing non-delegatable Console administrator boundary. */
class HubAdministratorPermissionTest {
    
    private static final String USERNAME = "hub-reader";
    
    private final RolePersistService roles = mock(RolePersistService.class);
    
    private final PermissionPersistService permissions = mock(PermissionPersistService.class);
    
    private final NacosUserService users = mock(NacosUserService.class);
    
    private NacosRoleServiceDirectImpl service;
    
    @BeforeEach
    void setUp() {
        NacosAuthPluginConfigProvider provider = mock(NacosAuthPluginConfigProvider.class);
        when(provider.getConfig()).thenReturn(mock(NacosAuthPluginConfig.class));
        service = new NacosRoleServiceDirectImpl(provider, roles, users, permissions);
    }
    
    @Test
    void globalAdministratorNeedsNoAdditionalHubPermission() {
        bind(AuthConstants.GLOBAL_ADMIN_ROLE);
        NacosUser user = new NacosUser(USERNAME);
        assertTrue(service.hasPermission(user, governance()));
        assertTrue(user.isGlobalAdmin());
        verifyNoInteractions(permissions);
    }
    
    @Test
    void consoleAdministratorResourceCannotBeDelegatedToAnOrdinaryRole() {
        bind("custom-role");
        grant("custom-role", AiConstants.Hub.ADMIN_RESOURCE);
        assertFalse(service.hasPermission(new NacosUser(USERNAME), governance()));
    }
    
    @Test
    void wildcardAclDoesNotBypassExistingAdministratorBoundary() {
        bind("custom-role");
        grant("custom-role", "*");
        assertFalse(service.hasPermission(new NacosUser(USERNAME), governance()));
    }
    
    @Test
    void namespaceOwnerKeepsSourceWriteButDoesNotGainGovernance() {
        bind("source-owner");
        grant("source-owner", "hub-test-source-a:*:*");
        NacosUser user = new NacosUser(USERNAME);
        assertTrue(service.hasPermission(user, new Permission(new Resource("hub-test-source-a", "",
            "find-skills", SignType.AI, new Properties()), "w")));
        assertFalse(service.hasPermission(user, governance()));
        assertFalse(user.isGlobalAdmin());
    }
    
    @Test
    void similarRoleNamesDoNotActAsGlobalAdministrator() {
        bind("ROLE_ADMIN_OTHER");
        assertFalse(service.hasPermission(new NacosUser(USERNAME), governance()));
    }
    
    @Test
    void usersWithoutRolesCannotManageHub() {
        assertFalse(service.hasPermission(new NacosUser(USERNAME), governance()));
        assertFalse(service.hasPermission(new NacosUser("anonymous"), governance()));
    }
    
    private void bind(String role) {
        when(roles.getRolesByUserNameAndRoleName(USERNAME, "", 1, Integer.MAX_VALUE))
            .thenReturn(page(Collections.singletonList(role(role))));
    }
    
    private RoleInfo role(String name) {
        RoleInfo role = new RoleInfo();
        role.setUsername(USERNAME);
        role.setRole(name);
        return role;
    }
    
    private void grant(String role, String resource) {
        PermissionInfo grant = new PermissionInfo();
        grant.setRole(role);
        grant.setResource(resource);
        grant.setAction("rw");
        when(permissions.getPermissions(role, 1, Integer.MAX_VALUE))
            .thenReturn(page(Collections.singletonList(grant)));
    }
    
    private <T> Page<T> page(List<T> items) {
        Page<T> page = new Page<>();
        page.setPageItems(items);
        page.setTotalCount(items.size());
        return page;
    }
    
    private Permission governance() {
        return new Permission(new Resource("", "", AiConstants.Hub.ADMIN_RESOURCE,
            SignType.SPECIFIED, new Properties()), "w");
    }
}
