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
import com.alibaba.nacos.config.server.model.Page;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * NacosRoleServiceImpl Test.
 *
 * @ClassName: NacosRoleServiceImplTest
 * @Author: ChenHao26
 * @Date: 2022/8/16 17:31
 * @Description: TODO
 */
@RunWith(MockitoJUnitRunner.class)
public class NacosRoleServiceImplTest {
    
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
    
    Class<NacosRoleServiceImpl> nacosRoleServiceClass;
    
    @Before
    public void setup() throws Exception {
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
    public void reload() throws Exception {
        Method reload = nacosRoleServiceClass.getDeclaredMethod("reload");
        reload.setAccessible(true);
        reload.invoke(nacosRoleService);
    }
    
    @Test
    public void hasPermission() {
        Permission permission = new Permission();
        permission.setAction("rw");
        permission.setResource(Resource.EMPTY_RESOURCE);
        NacosUser nacosUser = new NacosUser();
        nacosUser.setUserName("nacos");
        boolean res = nacosRoleService.hasPermission(nacosUser, permission);
        Assert.assertFalse(res);
        
        Permission permission2 = new Permission();
        permission2.setAction("rw");
        Resource resource = new Resource("public", "group", AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, "rw", null);
        permission2.setResource(resource);
        boolean res2 = nacosRoleService.hasPermission(nacosUser, permission2);
        Assert.assertTrue(res2);
    }
    
    @Test
    public void getRoles() {
        List<RoleInfo> nacos = nacosRoleService.getRoles("role-admin");
        Assert.assertEquals(nacos, Collections.emptyList());
    }
    
    @Test
    public void getRolesFromDatabase() {
        Page<RoleInfo> roleInfoPage = nacosRoleService.getRolesFromDatabase("nacos", "ROLE_ADMIN", 1,
                Integer.MAX_VALUE);
        Assert.assertEquals(roleInfoPage.getTotalCount(), 0);
    }
    
    @Test
    public void getPermissions() {
        boolean cachingEnabled = authConfigs.isCachingEnabled();
        Assert.assertFalse(cachingEnabled);
        List<PermissionInfo> permissions = nacosRoleService.getPermissions("role-admin");
        Assert.assertEquals(permissions, Collections.emptyList());
    }
    
    @Test
    public void getPermissionsByRoleFromDatabase() {
        Page<PermissionInfo> permissionsByRoleFromDatabase = nacosRoleService.getPermissionsByRoleFromDatabase(
                "role-admin", 1, Integer.MAX_VALUE);
        Assert.assertNull(permissionsByRoleFromDatabase);
    }
    
    @Test
    public void addRole() {
        String username = "nacos";
        User userFromDatabase = userDetailsService.getUserFromDatabase(username);
        Assert.assertNull(userFromDatabase);
        try {
            nacosRoleService.addRole("role-admin", "nacos");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("user 'nacos' not found!"));
        }
    }
    
    @Test
    public void deleteRole() {
        try {
            nacosRoleService.deleteRole("role-admin");
        } catch (Exception e) {
            Assert.assertNull(e);
        }
    }
    
    @Test
    public void getPermissionsFromDatabase() {
        Page<PermissionInfo> permissionsFromDatabase = nacosRoleService.getPermissionsFromDatabase("role-admin", 1,
                Integer.MAX_VALUE);
        Assert.assertEquals(permissionsFromDatabase.getTotalCount(), 0);
    }
    
    @Test
    public void addPermission() {
        try {
            nacosRoleService.addPermission("role-admin", "", "rw");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("role role-admin not found!"));
        }
    }
    
    @Test
    public void findRolesLikeRoleName() {
        List<String> rolesLikeRoleName = rolePersistService.findRolesLikeRoleName("role-admin");
        Assert.assertEquals(rolesLikeRoleName, Collections.emptyList());
    }
    
    @Test
    public void joinResource() throws Exception {
        Method method = nacosRoleServiceClass.getDeclaredMethod("joinResource", Resource.class);
        method.setAccessible(true);
        Resource resource = new Resource("public", "group", AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, "rw", null);
        Object invoke = method.invoke(nacosRoleService, new Resource[] {resource});
        Assert.assertNotNull(invoke);
    }
}
