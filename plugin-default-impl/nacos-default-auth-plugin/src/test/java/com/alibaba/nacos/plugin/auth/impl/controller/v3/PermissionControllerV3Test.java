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
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PermissionControllerV3Test.
 *
 * @author zhangyukun on:2024/9/5
 */
@ExtendWith(MockitoExtension.class)
public class PermissionControllerV3Test {
    
    @InjectMocks
    private PermissionControllerV3 permissionController;
    
    @Mock
    private NacosRoleService nacosRoleService;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(permissionController).build();
    }
    
    @Test
    void testGetPermissionListAccurateSearch() {
        Page<PermissionInfo> permissionInfoPage = new Page<>();
        when(nacosRoleService.getPermissions(anyString(), anyInt(), anyInt())).thenReturn(permissionInfoPage);
        
        Result<Page<PermissionInfo>> result = permissionController.getPermissionList(1, 10, "admin", "accurate");
        
        assertEquals(permissionInfoPage, result.getData());
        verify(nacosRoleService, times(1)).getPermissions("admin", 1, 10);
    }
    
    @Test
    void testGetPermissionListBlurSearch() {
        Page<PermissionInfo> permissionInfoPage = new Page<>();
        when(nacosRoleService.findPermissions(anyString(), anyInt(), anyInt())).thenReturn(permissionInfoPage);
        
        Result<Page<PermissionInfo>> result = permissionController.getPermissionList(1, 10, "admin", "blur");
        
        assertEquals(permissionInfoPage, result.getData());
        verify(nacosRoleService, times(1)).findPermissions("admin", 1, 10);
    }
    
    @Test
    void testCreatePermission() {
        Result<String> result = (Result<String>) permissionController.createPermission("admin", "testResource",
                "write");
        
        verify(nacosRoleService, times(1)).addPermission("admin", "testResource", "write");
        assertEquals("add permission ok!", result.getData());
    }
    
    @Test
    void testDeletePermission() {
        Result<String> result = (Result<String>) permissionController.deletePermission("admin", "testResource",
                "write");
        
        verify(nacosRoleService, times(1)).deletePermission("admin", "testResource", "write");
        assertEquals("delete permission ok!", result.getData());
    }
}