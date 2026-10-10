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

import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Exercises the default RBAC decision for the dedicated Hub governance resource.
 */
class HubManagementPermissionTest {
    
    private static final String HUB_MANAGE_RESOURCE = "ai-hub/manage";
    
    private final AbstractCheckedRoleService roleService =
        mock(AbstractCheckedRoleService.class, CALLS_REAL_METHODS);
    
    @Test
    void globalAdministratorMayManageHub() {
        grantRole(AuthConstants.GLOBAL_ADMIN_ROLE);
        assertTrue(
            roleService.hasPermission(new NacosUser("bob"), permission(HUB_MANAGE_RESOURCE)));
    }
    
    @Test
    void dedicatedManagerMayManageHubWithoutGlobalAdministratorRole() {
        grantRole("hub_manager");
        grantPermission(HUB_MANAGE_RESOURCE, "w");
        NacosUser user = new NacosUser("bob");
        assertTrue(roleService.hasPermission(user, permission(HUB_MANAGE_RESOURCE)));
        assertFalse(user.isGlobalAdmin());
        assertFalse(roleService.hasPermission(user, permission("console/ai-hub/manage")));
    }
    
    @Test
    void sourceWriteGrantDoesNotGrantHubGovernance() {
        grantRole("hub_manager");
        grantPermission("@@visibility/team-a/skill/find-skills", "w");
        assertFalse(
            roleService.hasPermission(new NacosUser("bob"), permission(HUB_MANAGE_RESOURCE)));
    }
    
    @Test
    void ordinaryUserWithoutGrantCannotManageHub() {
        grantRole("hub_manager");
        doReturn(Collections.emptyList()).when(roleService).getPermissions("hub_manager");
        assertFalse(
            roleService.hasPermission(new NacosUser("bob"), permission(HUB_MANAGE_RESOURCE)));
    }
    
    @Test
    void readOnlyGovernanceGrantCannotManageHub() {
        grantRole("hub_manager");
        grantPermission(HUB_MANAGE_RESOURCE, "r");
        assertFalse(
            roleService.hasPermission(new NacosUser("bob"), permission(HUB_MANAGE_RESOURCE)));
    }
    
    @Test
    void anonymousWithoutRoleCannotManageHub() {
        doReturn(Collections.emptyList()).when(roleService).getRoles("anonymous");
        assertFalse(
            roleService.hasPermission(new NacosUser("anonymous"), permission(HUB_MANAGE_RESOURCE)));
    }
    
    private void grantRole(String roleName) {
        RoleInfo role = new RoleInfo();
        role.setUsername("bob");
        role.setRole(roleName);
        doReturn(Collections.singletonList(role)).when(roleService).getRoles("bob");
    }
    
    private void grantPermission(String resource, String action) {
        PermissionInfo grant = new PermissionInfo();
        grant.setRole("hub_manager");
        grant.setResource(resource);
        grant.setAction(action);
        doReturn(Collections.singletonList(grant)).when(roleService).getPermissions("hub_manager");
    }
    
    private Permission permission(String resourceName) {
        Resource resource =
            new Resource("", "", resourceName, SignType.SPECIFIED, new Properties());
        return new Permission(resource, "w");
    }
}
