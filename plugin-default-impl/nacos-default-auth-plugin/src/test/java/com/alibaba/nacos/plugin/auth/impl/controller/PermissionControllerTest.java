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
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PermissionControllerTest {
    
    @InjectMocks
    private PermissionController permissionController;
    
    @Mock
    private NacosRoleServiceImpl nacosRoleService;
    
    @BeforeEach
    void setUp() throws Exception {
    
    }
    
    @Test
    void testGetPermissions() {
        Page<PermissionInfo> permissionInfoPage = new Page<PermissionInfo>();
        
        when(nacosRoleService.getPermissionsFromDatabase(anyString(), anyInt(), anyInt())).thenReturn(
                permissionInfoPage);
        
        Object permissions = permissionController.getPermissions(1, 10, "admin");
        assertEquals(permissionInfoPage, permissions);
    }
    
    @Test
    void testFuzzySearchPermission() {
        Page<PermissionInfo> permissionInfoPage = new Page<PermissionInfo>();
        
        when(nacosRoleService.findPermissionsLike4Page(anyString(), anyInt(), anyInt())).thenReturn(permissionInfoPage);
        
        Page<PermissionInfo> permissions = permissionController.fuzzySearchPermission(1, 10, "admin");
        assertEquals(permissionInfoPage, permissions);
    }
    
    @Test
    void testAddPermission() {
        
        RestResult<String> result = (RestResult<String>) permissionController.addPermission("admin", "test", "test");
        
        verify(nacosRoleService, times(1)).addPermission(anyString(), anyString(), anyString());
        assertEquals(200, result.getCode());
    }
    
    @Test
    void testDeletePermission() {
        RestResult<String> result = (RestResult<String>) permissionController.deletePermission("admin", "test", "test");
        
        verify(nacosRoleService, times(1)).deletePermission(anyString(), anyString(), anyString());
        assertEquals(200, result.getCode());
    }
    
}
