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

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NacosRoleServiceImpl Test.
 *
 * @ClassName: NacosRoleServiceImplTest
 * @Author: ChenHao26
 * @Date: 2022/8/16 17:31
 * @Description: TODO
 */
@ExtendWith(MockitoExtension.class)
class NacosRoleServiceImplTest {
    
    Class<NacosRoleServiceImpl> nacosRoleServiceClass;
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private RolePersistService rolePersistService;
    
    @Mock
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Mock
    private PermissionPersistService permissionPersistService;
    
    @Mock
    private NacosRoleServiceImpl nacosRoleService;
    
    @BeforeEach
    void setup() throws Exception {
        nacosRoleService = new NacosRoleServiceImpl();
        nacosRoleServiceClass = NacosRoleServiceImpl.class;
        Field authConfigsFile = nacosRoleServiceClass.getDeclaredField("authConfigs");
        authConfigsFile.setAccessible(true);
        authConfigsFile.set(nacosRoleService, authConfigs);
        
        Field rolePersistServiceFile = nacosRoleServiceClass.getDeclaredField("rolePersistService");
        rolePersistServiceFile.setAccessible(true);
        rolePersistServiceFile.set(nacosRoleService, rolePersistService);
        
        Field userDetailsServiceField = nacosRoleServiceClass.getDeclaredField("userDetailsService");
        userDetailsServiceField.setAccessible(true);
        userDetailsServiceField.set(nacosRoleService, userDetailsService);
        
        Field permissionPersistServiceField = nacosRoleServiceClass.getDeclaredField("permissionPersistService");
        permissionPersistServiceField.setAccessible(true);
        permissionPersistServiceField.set(nacosRoleService, permissionPersistService);
    }
    
    @Test
    void reload() throws Exception {
        Method reload = nacosRoleServiceClass.getDeclaredMethod("reload");
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
        Resource resource = new Resource("public", "group", AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, "rw", null);
        permission2.setResource(resource);
        boolean res2 = nacosRoleService.hasPermission(nacosUser, permission2);
        assertTrue(res2);
    }
    
    @Test
    void getRoles() {
        List<RoleInfo> nacos = nacosRoleService.getRoles("role-admin");
        assertEquals(nacos, Collections.emptyList());
    }
    
    @Test
    void getRolesFromDatabase() {
        Page<RoleInfo> roleInfoPage = nacosRoleService.getRolesFromDatabase("nacos", "ROLE_ADMIN", 1, Integer.MAX_VALUE);
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
    void getPermissionsByRoleFromDatabase() {
        Page<PermissionInfo> permissionsByRoleFromDatabase = nacosRoleService.getPermissionsByRoleFromDatabase("role-admin", 1,
                Integer.MAX_VALUE);
        assertNull(permissionsByRoleFromDatabase);
    }
    
    @Test
    void addRole() {
        String username = "nacos";
        User userFromDatabase = userDetailsService.getUserFromDatabase(username);
        assertNull(userFromDatabase);
        try {
            nacosRoleService.addRole("role-admin", "nacos");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("user 'nacos' not found!"));
        }
    }
    
    @Test
    void deleteRole() {
        try {
            nacosRoleService.deleteRole("role-admin");
        } catch (Exception e) {
            assertNull(e);
        }
    }
    
    @Test
    void getPermissionsFromDatabase() {
        Page<PermissionInfo> permissionsFromDatabase = nacosRoleService.getPermissionsFromDatabase("role-admin", 1, Integer.MAX_VALUE);
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
        Method method = nacosRoleServiceClass.getDeclaredMethod("joinResource", Resource.class);
        method.setAccessible(true);
        Resource resource = new Resource("public", "group", AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, "rw", null);
        Object invoke = method.invoke(nacosRoleService, new Resource[] {resource});
        assertNotNull(invoke);
    }
}
