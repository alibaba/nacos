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
 *
 */

package com.alibaba.nacos.plugin.auth.impl.controller.v3;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RoleControllerV3Test.
 *
 * @author zhangyukun on:2024/9/5
 */
@ExtendWith(MockitoExtension.class)
public class RoleControllerV3Test {
    
    @Mock
    private NacosRoleService roleService;
    
    @InjectMocks
    private RoleControllerV3 roleControllerV3;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(roleControllerV3).build();
    }
    
    @Test
    void testCreateRole() {
        Result<String> result = (Result<String>) roleControllerV3.createRole("test", "nacos");
        
        verify(roleService, times(1)).addRole(anyString(), anyString());
        
        assertEquals("add role ok!", result.getData());
    }
    
    @Test
    void testDeleteRoleWithoutUsername() {
        Result<String> result = (Result<String>) roleControllerV3.deleteRole("test", "");
        
        verify(roleService, times(1)).deleteRole(anyString());
        
        assertEquals("delete role of user  ok!", result.getData());
    }
    
    @Test
    void testDeleteRoleWithUsername() {
        Result<String> result = (Result<String>) roleControllerV3.deleteRole("test", "nacos");
        
        verify(roleService, times(1)).deleteRole(anyString(), anyString());
        
        assertEquals("delete role of user nacos ok!", result.getData());
    }
    
    @Test
    void testGetRoleListAccurateSearch() {
        Page<RoleInfo> rolesTest = new Page<RoleInfo>();
        
        when(roleService.getRoles(anyString(), anyString(), anyInt(), anyInt())).thenReturn(rolesTest);
        
        Result<Page<RoleInfo>> result = roleControllerV3.getRoleList(1, 10, "nacos", "test", "accurate");
        
        assertEquals(rolesTest, result.getData());
    }
    
    @Test
    void testGetRoleListFuzzySearch() {
        Page<RoleInfo> rolesTest = new Page<RoleInfo>();
        
        when(roleService.findRoles(anyString(), anyString(), anyInt(), anyInt())).thenReturn(rolesTest);
        
        Result<Page<RoleInfo>> result = roleControllerV3.getRoleList(1, 10, "nacos", "test", "blur");
        
        assertEquals(rolesTest, result.getData());
    }
    
    @Test
    void testGetRoleListByRoleName() {
        List<String> rolesTest = new ArrayList<>();
        
        when(roleService.findRoleNames(anyString())).thenReturn(rolesTest);
        
        Result<List<String>> result = roleControllerV3.getRoleListByRoleName("test");
        
        assertEquals(rolesTest, result.getData());
    }
}
