/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * NacosRoleServiceImpl Test.
 */
@ExtendWith(MockitoExtension.class)
class NacosRoleServiceDirectImplTest {
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private RolePersistService rolePersistService;
    
    @Mock
    private NacosUserService userDetailsService;
    
    @Mock
    private PermissionPersistService permissionPersistService;
    
    @Mock
    private NacosRoleServiceDirectImpl nacosRoleService;
    
    @BeforeEach
    void setup() throws Exception {
        nacosRoleService = new NacosRoleServiceDirectImpl(authConfigs, rolePersistService, userDetailsService,
                permissionPersistService);
    }
    
    @Test
    void reload() throws Exception {
        Method reload = AbstractCachedRoleService.class.getDeclaredMethod("reload");
        reload.setAccessible(true);
        reload.invoke(nacosRoleService);
    }
    
    @Test
    void hasPermission() {
        Permission permission = new Permission();
        permission.setAction("rw");
        permission.setResource(Resource.EMPTY_RESOURCE);
        NacosUser nacosUser = new NacosUser();
        nacosUser.setUserName("nacos");
        boolean res = nacosRoleService.hasPermission(nacosUser, permission);
        assertFalse(res);
        
        Permission permission2 = new Permission();
        permission2.setAction("rw");
        Resource resource = new Resource("public", "group", AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, "rw",
                new Properties());
        permission2.setResource(resource);
        boolean res2 = nacosRoleService.hasPermission(nacosUser, permission2);
        assertFalse(res2);
        resource.getProperties()
                .put(AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, AuthConstants.UPDATE_PASSWORD_ENTRY_POINT);
        boolean res3 = nacosRoleService.hasPermission(nacosUser, permission2);
        assertTrue(res3);
    }
    
    @Test
    void getRoles() {
        List<RoleInfo> nacos = nacosRoleService.getRoles("role-admin");
        assertEquals(nacos, Collections.emptyList());
    }
    
    @Test
    void getRolesPage() {
        Page<RoleInfo> roleInfoPage = nacosRoleService.getRoles("nacos", "ROLE_ADMIN", 1, Integer.MAX_VALUE);
        assertEquals(0, roleInfoPage.getTotalCount());
    }
    
    @Test
    void getPermissions() {
        boolean cachingEnabled = authConfigs.isCachingEnabled();
        assertFalse(cachingEnabled);
        List<PermissionInfo> permissions = nacosRoleService.getPermissions("role-admin");
        assertEquals(permissions, Collections.emptyList());
    }
    
    @Test
    void addRole() {
        String username = "nacos";
        User userFromDatabase = userDetailsService.getUser(username);
        assertNull(userFromDatabase);
        try {
            nacosRoleService.addRole("role-admin", "nacos");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("user 'nacos' not found!"));
        }
    }
    
    @Test
    void deleteRole() {
        assertDoesNotThrow(() -> nacosRoleService.deleteRole("role-admin"));
        assertDoesNotThrow(() -> nacosRoleService.deleteRole("mockRole", "mockUser"));
    }
    
    @Test
    void deleteAdminRole() {
        assertThrows(IllegalArgumentException.class, () -> nacosRoleService.deleteRole(AuthConstants.GLOBAL_ADMIN_ROLE),
                "role 'ROLE_ADMIN' is not permitted to delete!");
        assertThrows(IllegalArgumentException.class,
                () -> nacosRoleService.deleteRole(AuthConstants.GLOBAL_ADMIN_ROLE, "mockUser"),
                "role 'ROLE_ADMIN' is not permitted to delete!");
    }
    
    @Test
    void getPermissionsPage() {
        Page<PermissionInfo> permissionsFromDatabase = nacosRoleService.getPermissions("role-admin", 1,
                Integer.MAX_VALUE);
        assertEquals(0, permissionsFromDatabase.getTotalCount());
    }
    
    @Test
    void addPermission() {
        try {
            nacosRoleService.addPermission("role-admin", "", "rw");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("role role-admin not found!"));
        }
    }
    
    @Test
    void findRolesLikeRoleName() {
        List<String> rolesLikeRoleName = rolePersistService.findRolesLikeRoleName("role-admin");
        assertEquals(rolesLikeRoleName, Collections.emptyList());
    }
    
    @Test
    void joinResource() throws Exception {
        Method method = AbstractCheckedRoleService.class.getDeclaredMethod("joinResource", Resource.class);
        method.setAccessible(true);
        Resource resource = new Resource("public", "group", AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, "rw", null);
        Object invoke = method.invoke(nacosRoleService, new Resource[] {resource});
        assertNotNull(invoke);
    }
    
    @Test
    void duplicatePermission() {
        List<PermissionInfo> permissionInfos = new ArrayList<>();
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.setAction("rw");
        permissionInfo.setResource("test");
        permissionInfos.add(permissionInfo);
        NacosRoleServiceDirectImpl spy = spy(nacosRoleService);
        when(spy.getPermissions("admin")).thenReturn(permissionInfos);
        spy.isDuplicatePermission("admin", "test", "r");
    }
    
    @Test
    void isUserBoundToRole() {
        String role = "TEST";
        String userName = "nacos";
        assertFalse(nacosRoleService.isUserBoundToRole("", userName));
        assertFalse(nacosRoleService.isUserBoundToRole(role, ""));
        assertFalse(nacosRoleService.isUserBoundToRole("", null));
        assertFalse(nacosRoleService.isUserBoundToRole(null, ""));
        assertFalse(nacosRoleService.isUserBoundToRole(role, userName));
    }
}
