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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnonymousAccessInitializerTest {
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private UserPersistService userPersistService;
    
    @Mock
    private RolePersistService rolePersistService;
    
    @Mock
    private PermissionPersistService permissionPersistService;
    
    private AnonymousAccessInitializer initializer;
    
    @BeforeEach
    void setUp() {
        initializer =
            new AnonymousAccessInitializer(authConfigs, userPersistService, rolePersistService,
                permissionPersistService);
    }
    
    @Test
    void testInitWhenDisabled() {
        when(authConfigs.isAiAnonymousEnabled()).thenReturn(false);
        
        initializer.init();
        
        verifyNoInteractions(userPersistService, rolePersistService, permissionPersistService);
    }
    
    @Test
    void testInitCreatesUserRolePermission() {
        when(authConfigs.isAiAnonymousEnabled()).thenReturn(true);
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER)).thenReturn(null);
        
        initializer.init();
        
        verify(userPersistService).findUserByUsername(AuthConstants.ANONYMOUS_USER);
        verify(userPersistService, times(1)).createUser(eq("__nacos_anonymous__"), anyString());
        verify(rolePersistService, times(1)).addRole("__nacos_anonymous_role__",
            "__nacos_anonymous__");
        verify(permissionPersistService, times(1)).addPermission("__nacos_anonymous_role__",
            "public:*:ai/*", "r");
    }
    
    @Test
    void testInitSkipsExistingUser() {
        when(authConfigs.isAiAnonymousEnabled()).thenReturn(true);
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER))
            .thenReturn(new User());
        
        initializer.init();
        
        verify(userPersistService, never()).createUser(anyString(), anyString());
        verify(rolePersistService, times(1)).addRole(AuthConstants.ANONYMOUS_ROLE,
            AuthConstants.ANONYMOUS_USER);
        verify(permissionPersistService, times(1)).addPermission(AuthConstants.ANONYMOUS_ROLE,
            "public:*:ai/*", "r");
    }
    
    @Test
    void testInitHandlesRoleCreationException() {
        when(authConfigs.isAiAnonymousEnabled()).thenReturn(true);
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER)).thenReturn(null);
        doThrow(new RuntimeException("duplicate role")).when(rolePersistService)
            .addRole(AuthConstants.ANONYMOUS_ROLE, AuthConstants.ANONYMOUS_USER);
        
        assertDoesNotThrow(() -> initializer.init());
        
        verify(userPersistService, times(1)).createUser(eq(AuthConstants.ANONYMOUS_USER),
            anyString());
        verify(permissionPersistService, times(1)).addPermission(AuthConstants.ANONYMOUS_ROLE,
            "public:*:ai/*", "r");
    }
    
    @Test
    void testInitHandlesPermissionCreationException() {
        when(authConfigs.isAiAnonymousEnabled()).thenReturn(true);
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER)).thenReturn(null);
        doThrow(new RuntimeException("duplicate permission")).when(permissionPersistService)
            .addPermission(AuthConstants.ANONYMOUS_ROLE, "public:*:ai/*", "r");
        
        assertDoesNotThrow(() -> initializer.init());
        
        verify(userPersistService, times(1)).createUser(eq(AuthConstants.ANONYMOUS_USER),
            anyString());
        verify(rolePersistService, times(1)).addRole(AuthConstants.ANONYMOUS_ROLE,
            AuthConstants.ANONYMOUS_USER);
    }
}
