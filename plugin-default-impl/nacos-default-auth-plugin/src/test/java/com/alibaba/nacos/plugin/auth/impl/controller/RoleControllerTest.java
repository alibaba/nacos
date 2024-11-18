/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.controller;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RoleControllerTest {
    
    @Mock
    private NacosRoleServiceImpl roleService;
    
    @InjectMocks
    private RoleController roleController;
    
    @BeforeEach
    void setUp() throws Exception {
    
    }
    
    @Test
    void testGetRoles() {
        Page<RoleInfo> rolesTest = new Page<RoleInfo>();
        
        when(roleService.getRolesFromDatabase(anyString(), anyString(), anyInt(), anyInt())).thenReturn(rolesTest);
        Object roles = roleController.getRoles(1, 10, "nacos", "test");
        
        assertEquals(rolesTest, roles);
    }
    
    @Test
    void testFuzzySearchRole() {
        
        Page<RoleInfo> rolesTest = new Page<RoleInfo>();
        
        when(roleService.findRolesLike4Page(anyString(), anyString(), anyInt(), anyInt())).thenReturn(rolesTest);
        
        Page<RoleInfo> roleInfoPage = roleController.fuzzySearchRole(1, 10, "nacos", "test");
        
        assertEquals(rolesTest, roleInfoPage);
    }
    
    @Test
    void testSearchRoles() {
        List<String> test = new ArrayList<>();
        
        when(roleService.findRolesLikeRoleName(anyString())).thenReturn(test);
        
        List<String> list = roleController.searchRoles("test");
        assertEquals(test, list);
    }
    
    @Test
    void testAddRole() {
        RestResult<String> result = (RestResult<String>) roleController.addRole("test", "nacos");
        
        verify(roleService, times(1)).addRole(anyString(), anyString());
        
        assertEquals(200, result.getCode());
    }
    
    @Test
    void testDeleteRole1() {
        RestResult<String> result = (RestResult<String>) roleController.deleteRole("test", null);
        
        verify(roleService, times(1)).deleteRole(anyString());
        
        assertEquals(200, result.getCode());
        
    }
    
    @Test
    void testDeleteRole2() {
        RestResult<String> result = (RestResult<String>) roleController.deleteRole("test", "nacos");
        
        verify(roleService, times(1)).deleteRole(anyString(), anyString());
        
        assertEquals(200, result.getCode());
        
    }
}
