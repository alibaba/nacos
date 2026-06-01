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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
        nacosRoleService =
            new NacosRoleServiceDirectImpl(authConfigs, rolePersistService, userDetailsService,
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
        Resource resource =
            new Resource("public", "group", AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, "rw",
                new Properties());
        permission2.setResource(resource);
        boolean res2 = nacosRoleService.hasPermission(nacosUser, permission2);
        assertFalse(res2);
        resource.getProperties()
            .put(AuthConstants.UPDATE_PASSWORD_ENTRY_POINT,
                AuthConstants.UPDATE_PASSWORD_ENTRY_POINT);
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
        Page<RoleInfo> roleInfoPage =
            nacosRoleService.getRoles("nacos", "ROLE_ADMIN", 1, Integer.MAX_VALUE);
        assertEquals(0, roleInfoPage.getTotalCount());
    }
    
    @Test
    void getRolesLoadsAndCachesRoleItems() {
        RoleInfo roleInfo = roleInfo("role-admin", "nacos");
        when(authConfigs.isCachingEnabled()).thenReturn(true);
        when(rolePersistService.getRolesByUserNameAndRoleName("nacos", "", 1,
            Integer.MAX_VALUE)).thenReturn(rolePage(Collections.singletonList(roleInfo)));
        
        List<RoleInfo> firstResult = nacosRoleService.getRoles("nacos");
        List<RoleInfo> cachedResult = nacosRoleService.getRoles("nacos");
        
        assertEquals(Collections.singletonList(roleInfo), firstResult);
        assertSame(firstResult, cachedResult);
        verify(rolePersistService, times(1)).getRolesByUserNameAndRoleName("nacos", "", 1,
            Integer.MAX_VALUE);
    }
    
    @Test
    void getAllRolesReturnsPageItemsAndNullPage() {
        RoleInfo roleInfo = roleInfo("role-admin", "nacos");
        when(rolePersistService.getRolesByUserNameAndRoleName("", "", 1,
            Integer.MAX_VALUE)).thenReturn(rolePage(Collections.singletonList(roleInfo)))
            .thenReturn(null);
        
        assertEquals(Collections.singletonList(roleInfo), nacosRoleService.getAllRoles());
        assertNull(nacosRoleService.getAllRoles());
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
        assertThrows(IllegalArgumentException.class,
            () -> nacosRoleService.deleteRole(AuthConstants.GLOBAL_ADMIN_ROLE),
            "role 'ROLE_ADMIN' is not permitted to delete!");
        assertThrows(IllegalArgumentException.class,
            () -> nacosRoleService.deleteRole(AuthConstants.GLOBAL_ADMIN_ROLE, "mockUser"),
            "role 'ROLE_ADMIN' is not permitted to delete!");
    }
    
    @Test
    void addAnonymousRoleRejected() {
        when(userDetailsService.getUser("testUser")).thenReturn(new User());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> nacosRoleService.addRole(AuthConstants.ANONYMOUS_ROLE, "testUser"));
        assertTrue(exception.getMessage().contains("reserved by the system"));
    }
    
    @Test
    void deleteAnonymousRoleRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> nacosRoleService.deleteRole(AuthConstants.ANONYMOUS_ROLE));
        assertThrows(IllegalArgumentException.class,
            () -> nacosRoleService.deleteRole(AuthConstants.ANONYMOUS_ROLE, "mockUser"));
    }
    
    @Test
    void getPermissionsPage() {
        Page<PermissionInfo> permissionsFromDatabase =
            nacosRoleService.getPermissions("role-admin", 1,
                Integer.MAX_VALUE);
        assertEquals(0, permissionsFromDatabase.getTotalCount());
    }
    
    @Test
    void getPermissionsLoadsAndCachesPermissionItems() {
        PermissionInfo permissionInfo = permissionInfo("role-admin", "resource", "rw");
        when(authConfigs.isCachingEnabled()).thenReturn(true);
        when(permissionPersistService.getPermissions("role-admin", 1,
            Integer.MAX_VALUE)).thenReturn(permissionPage(
                Collections.singletonList(
                    permissionInfo)));
        
        List<PermissionInfo> firstResult = nacosRoleService.getPermissions("role-admin");
        List<PermissionInfo> cachedResult = nacosRoleService.getPermissions("role-admin");
        
        assertEquals(Collections.singletonList(permissionInfo), firstResult);
        assertSame(firstResult, cachedResult);
        verify(permissionPersistService, times(1)).getPermissions("role-admin", 1,
            Integer.MAX_VALUE);
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
    void addRoleBindsExistingUserAndUpdatesCache() {
        when(userDetailsService.getUser("nacos")).thenReturn(new User());
        when(rolePersistService.getRolesByUserNameAndRoleName("nacos", "role-admin", 1,
            1)).thenReturn(new Page<>());
        
        nacosRoleService.addRole("role-admin", "nacos");
        
        verify(rolePersistService).addRole("role-admin", "nacos");
        assertTrue(nacosRoleService.getCachedRoleSet().contains("role-admin"));
    }
    
    @Test
    void addRoleRejectsReservedAdminRole() {
        when(userDetailsService.getUser("nacos")).thenReturn(new User());
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> nacosRoleService.addRole(AuthConstants.GLOBAL_ADMIN_ROLE, "nacos"));
        
        assertTrue(exception.getMessage().contains("is not permitted to create"));
    }
    
    @Test
    void addRoleRejectsAlreadyBoundRole() {
        RoleInfo roleInfo = roleInfo("role-admin", "nacos");
        when(userDetailsService.getUser("nacos")).thenReturn(new User());
        when(rolePersistService.getRolesByUserNameAndRoleName("nacos", "role-admin", 1,
            1)).thenReturn(rolePage(Collections.singletonList(roleInfo)));
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> nacosRoleService.addRole("role-admin", "nacos"));
        
        assertTrue(exception.getMessage().contains("already bound"));
    }
    
    @Test
    void addAdminRoleBindsFirstAdminAndMarksConfig() {
        when(userDetailsService.getUser("nacos")).thenReturn(new User());
        when(authConfigs.isHasGlobalAdminRole()).thenReturn(false);
        when(rolePersistService.getRolesByUserNameAndRoleName("", "", 1,
            Integer.MAX_VALUE)).thenReturn(rolePage(Collections.emptyList()));
        
        nacosRoleService.addAdminRole("nacos");
        
        verify(rolePersistService).addRole(AuthConstants.GLOBAL_ADMIN_ROLE, "nacos");
        verify(authConfigs).setHasGlobalAdminRole(true);
        assertTrue(nacosRoleService.getCachedRoleSet().contains(AuthConstants.GLOBAL_ADMIN_ROLE));
    }
    
    @Test
    void addAdminRoleRejectsMissingUserAndExistingAdminRole() {
        assertThrows(IllegalArgumentException.class, () -> nacosRoleService.addAdminRole("nacos"));
        
        when(userDetailsService.getUser("nacos")).thenReturn(new User());
        when(authConfigs.isHasGlobalAdminRole()).thenReturn(true);
        
        assertThrows(IllegalArgumentException.class, () -> nacosRoleService.addAdminRole("nacos"));
    }
    
    @Test
    void addPermissionAndDeletePermissionDelegateToPersistService() {
        nacosRoleService.getCachedRoleSet().add("role-admin");
        
        nacosRoleService.addPermission("role-admin", "resource", "rw");
        nacosRoleService.deletePermission("role-admin", "resource", "rw");
        
        verify(permissionPersistService).addPermission("role-admin", "resource", "rw");
        verify(permissionPersistService).deletePermission("role-admin", "resource", "rw");
    }
    
    @Test
    void findRolesLikeRoleName() {
        List<String> rolesLikeRoleName = rolePersistService.findRolesLikeRoleName("role-admin");
        assertEquals(rolesLikeRoleName, Collections.emptyList());
    }
    
    @Test
    void findDelegatesToPersistServices() {
        Page<RoleInfo> rolePage = rolePage(Collections.singletonList(roleInfo("role-admin",
            "nacos")));
        Page<PermissionInfo> permissionPage = permissionPage(Collections.singletonList(
            permissionInfo("role-admin", "resource", "rw")));
        when(rolePersistService.findRolesLike4Page("nacos", "role", 1, 10))
            .thenReturn(rolePage);
        when(rolePersistService.findRolesLikeRoleName("role"))
            .thenReturn(Collections.singletonList("role-admin"));
        when(permissionPersistService.findPermissionsLike4Page("role", 1, 10))
            .thenReturn(permissionPage);
        
        assertSame(rolePage, nacosRoleService.findRoles("nacos", "role", 1, 10));
        assertEquals(Collections.singletonList("role-admin"),
            nacosRoleService.findRoleNames("role"));
        assertSame(permissionPage, nacosRoleService.findPermissions("role", 1, 10));
    }
    
    @Test
    void joinResource() throws Exception {
        Method method =
            AbstractCheckedRoleService.class.getDeclaredMethod("joinResource", Resource.class);
        method.setAccessible(true);
        Resource resource =
            new Resource("public", "group", AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, "rw", null);
        Object invoke = method.invoke(nacosRoleService, new Resource[] {resource});
        assertNotNull(invoke);
    }
    
    @Test
    void joinResourceHandlesSpecifiedAndBlankFields() throws Exception {
        Method method =
            AbstractCheckedRoleService.class.getDeclaredMethod("joinResource", Resource.class);
        method.setAccessible(true);
        
        Object specified = method.invoke(nacosRoleService,
            new Resource[] {new Resource("public", "", "raw-resource", "specified", null)});
        Object defaulted = method.invoke(nacosRoleService,
            new Resource[] {new Resource("", "", "", "naming", null)});
        
        assertEquals("raw-resource", specified);
        assertEquals("public:*:naming/*", defaulted);
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
    void duplicatePermissionReturnsExpectedResult() {
        PermissionInfo readWritePermission = permissionInfo("admin", "test", "rw");
        PermissionInfo readPermission = permissionInfo("admin", "data", "r");
        NacosRoleServiceDirectImpl spy = spy(nacosRoleService);
        doReturn(Collections.emptyList()).when(spy).getPermissions("empty");
        doReturn(java.util.Arrays.asList(readWritePermission, readPermission)).when(spy)
            .getPermissions("admin");
        
        assertFalse(spy.isDuplicatePermission("empty", "test", "r").getData());
        assertTrue(spy.isDuplicatePermission("admin", "test", "r").getData());
        assertTrue(spy.isDuplicatePermission("admin", "data", "r").getData());
        assertFalse(spy.isDuplicatePermission("admin", "data", "w").getData());
    }
    
    @Test
    void hasPermissionReturnsTrueForGlobalAdminRole() {
        RoleInfo roleInfo = roleInfo(AuthConstants.GLOBAL_ADMIN_ROLE, "nacos");
        when(authConfigs.isCachingEnabled()).thenReturn(false);
        when(rolePersistService.getRolesByUserNameAndRoleName("nacos", "", 1,
            Integer.MAX_VALUE)).thenReturn(rolePage(Collections.singletonList(roleInfo)));
        NacosUser nacosUser = new NacosUser("nacos");
        Permission permission = new Permission();
        permission.setAction("r");
        permission.setResource(new Resource("public", "group", "service", "naming",
            new Properties()));
        
        assertTrue(nacosRoleService.hasPermission(nacosUser, permission));
        assertTrue(nacosUser.isGlobalAdmin());
    }
    
    @Test
    void hasPermissionReturnsTrueForMatchedPermissionPattern() {
        RoleInfo roleInfo = roleInfo("reader", "nacos");
        PermissionInfo permissionInfo = permissionInfo("reader", "public:group:naming/*", "rw");
        when(authConfigs.isCachingEnabled()).thenReturn(false);
        when(rolePersistService.getRolesByUserNameAndRoleName("nacos", "", 1,
            Integer.MAX_VALUE)).thenReturn(rolePage(Collections.singletonList(roleInfo)));
        when(permissionPersistService.getPermissions("reader", 1,
            Integer.MAX_VALUE)).thenReturn(permissionPage(
                Collections.singletonList(
                    permissionInfo)));
        Permission permission = new Permission();
        permission.setAction("r");
        permission.setResource(new Resource("public", "group", "service", "naming",
            new Properties()));
        
        assertTrue(nacosRoleService.hasPermission(new NacosUser("nacos"), permission));
    }
    
    @Test
    void hasPermissionReturnsFalseForConsoleResourceWithoutGlobalAdmin() {
        RoleInfo roleInfo = roleInfo("reader", "nacos");
        when(authConfigs.isCachingEnabled()).thenReturn(false);
        when(rolePersistService.getRolesByUserNameAndRoleName("nacos", "", 1,
            Integer.MAX_VALUE)).thenReturn(rolePage(Collections.singletonList(roleInfo)));
        Permission permission = new Permission();
        permission.setAction("r");
        permission.setResource(new Resource("public", "group", "console/users", "console",
            new Properties()));
        
        assertFalse(nacosRoleService.hasPermission(new NacosUser("nacos"), permission));
    }
    
    @Test
    void hasPermissionSkipsEmptyPermissionsThenMatchesDefaultNamespacePattern() {
        RoleInfo emptyRole = roleInfo("empty", "nacos");
        RoleInfo reader = roleInfo("reader", "nacos");
        PermissionInfo permissionInfo = permissionInfo("reader", ":group:naming/service", "rw");
        when(authConfigs.isCachingEnabled()).thenReturn(false);
        when(rolePersistService.getRolesByUserNameAndRoleName("nacos", "", 1,
            Integer.MAX_VALUE)).thenReturn(rolePage(java.util.Arrays.asList(emptyRole, reader)));
        when(permissionPersistService.getPermissions("empty", 1,
            Integer.MAX_VALUE)).thenReturn(permissionPage(Collections.emptyList()));
        when(permissionPersistService.getPermissions("reader", 1,
            Integer.MAX_VALUE)).thenReturn(permissionPage(
                Collections.singletonList(
                    permissionInfo)));
        Permission permission = new Permission();
        permission.setAction("r");
        permission.setResource(new Resource("", "group", "service", "naming", new Properties()));
        
        assertTrue(nacosRoleService.hasPermission(new NacosUser("nacos"), permission));
    }
    
    @Test
    void hasGlobalAdminRoleChecksUserAndCachedAllRoles() {
        RoleInfo adminRole = roleInfo(AuthConstants.GLOBAL_ADMIN_ROLE, "nacos");
        when(authConfigs.isCachingEnabled()).thenReturn(false);
        when(rolePersistService.getRolesByUserNameAndRoleName("nacos", "", 1,
            Integer.MAX_VALUE)).thenReturn(rolePage(Collections.singletonList(adminRole)));
        
        assertTrue(nacosRoleService.hasGlobalAdminRole("nacos"));
        
        when(authConfigs.isHasGlobalAdminRole()).thenReturn(true);
        assertTrue(nacosRoleService.hasGlobalAdminRole());
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
    
    private RoleInfo roleInfo(String role, String username) {
        RoleInfo roleInfo = new RoleInfo();
        roleInfo.setRole(role);
        roleInfo.setUsername(username);
        return roleInfo;
    }
    
    private PermissionInfo permissionInfo(String role, String resource, String action) {
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.setRole(role);
        permissionInfo.setResource(resource);
        permissionInfo.setAction(action);
        return permissionInfo;
    }
    
    private Page<RoleInfo> rolePage(List<RoleInfo> roleInfos) {
        Page<RoleInfo> page = new Page<>();
        page.setPageItems(roleInfos);
        page.setTotalCount(roleInfos.size());
        return page;
    }
    
    private Page<PermissionInfo> permissionPage(List<PermissionInfo> permissionInfos) {
        Page<PermissionInfo> page = new Page<>();
        page.setPageItems(permissionInfos);
        page.setTotalCount(permissionInfos.size());
        return page;
    }
}
