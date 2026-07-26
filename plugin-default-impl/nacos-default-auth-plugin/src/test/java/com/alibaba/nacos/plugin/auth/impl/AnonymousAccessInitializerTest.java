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

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfigProvider;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnonymousAccessInitializerTest {
    
    @Mock
    private NacosAuthPluginConfigProvider configProvider;
    
    @Mock
    private UserPersistService userPersistService;
    
    @Mock
    private RolePersistService rolePersistService;
    
    @Mock
    private PermissionPersistService permissionPersistService;
    
    @Test
    void testDisabledDoesNotAccessPersistence() {
        when(configProvider.getConfig()).thenReturn(config(false));
        AnonymousAccessInitializer initializer = new AnonymousAccessInitializer(configProvider,
            userPersistService, rolePersistService, permissionPersistService);
        initializer.requestReconcile();
        initializer.reconcile();
        verifyNoInteractions(userPersistService, rolePersistService, permissionPersistService);
    }
    
    @Test
    void testRequestCreatesIdentityAndStopsWhenReady() {
        enableAnonymousAccess();
        when(rolePersistService.getRolesByUserNameAndRoleName(anyString(), anyString(),
            eq(1), eq(1))).thenReturn(rolePage(false), rolePage(true));
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER)).thenReturn(null);
        when(permissionPersistService.getPermissions(AuthConstants.ANONYMOUS_ROLE, 1,
            Integer.MAX_VALUE)).thenReturn(permissionPage(false));
        AnonymousAccessInitializer initializer = initializer(Runnable::run);
        initializer.requestReconcile();
        initializer.requestReconcile();
        initializer.reconcile();
        verify(userPersistService).createUser(eq(AuthConstants.ANONYMOUS_USER), anyString());
        verify(permissionPersistService).addPermission(AuthConstants.ANONYMOUS_ROLE,
            "public:*:ai/*", "r");
        verify(rolePersistService).addRole(AuthConstants.ANONYMOUS_ROLE,
            AuthConstants.ANONYMOUS_USER);
    }
    
    @Test
    void testExistingRoleBindingPreservesManagedPermissions() {
        enableAnonymousAccess();
        when(rolePersistService.getRolesByUserNameAndRoleName(anyString(), anyString(),
            eq(1), eq(1))).thenReturn(rolePage(true));
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER))
            .thenReturn(new User());
        initializer(Runnable::run).reconcile();
        verify(userPersistService, never()).createUser(anyString(), anyString());
        verifyNoInteractions(permissionPersistService);
        verify(rolePersistService, never()).addRole(anyString(), anyString());
    }
    
    @Test
    void testExistingDefaultPermissionIsNotDuplicated() {
        enableAnonymousAccess();
        when(rolePersistService.getRolesByUserNameAndRoleName(anyString(), anyString(),
            eq(1), eq(1))).thenReturn(rolePage(false), rolePage(true));
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER))
            .thenReturn(new User());
        when(permissionPersistService.getPermissions(AuthConstants.ANONYMOUS_ROLE, 1,
            Integer.MAX_VALUE)).thenReturn(permissionPage(true));
        initializer(Runnable::run).reconcile();
        verify(permissionPersistService, never()).addPermission(anyString(), anyString(),
            anyString());
    }
    
    @Test
    void testConcurrentDuplicateWritesAreConfirmedByReadBack() {
        enableAnonymousAccess();
        when(rolePersistService.getRolesByUserNameAndRoleName(anyString(), anyString(),
            eq(1), eq(1))).thenReturn(rolePage(false), rolePage(true), rolePage(true));
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER))
            .thenReturn(null, new User());
        when(permissionPersistService.getPermissions(AuthConstants.ANONYMOUS_ROLE, 1,
            Integer.MAX_VALUE)).thenReturn(permissionPage(false), permissionPage(true));
        doThrow(new RuntimeException("duplicate user")).when(userPersistService)
            .createUser(eq(AuthConstants.ANONYMOUS_USER), anyString());
        doThrow(new RuntimeException("duplicate permission")).when(permissionPersistService)
            .addPermission(AuthConstants.ANONYMOUS_ROLE, "public:*:ai/*", "r");
        doThrow(new RuntimeException("duplicate role")).when(rolePersistService)
            .addRole(AuthConstants.ANONYMOUS_ROLE, AuthConstants.ANONYMOUS_USER);
        assertDoesNotThrow(() -> initializer(Runnable::run).reconcile());
    }
    
    @Test
    void testFailureIsRetriedAndRateLimited() {
        enableAnonymousAccess();
        when(rolePersistService.getRolesByUserNameAndRoleName(anyString(), anyString(),
            eq(1), eq(1))).thenReturn(null);
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER)).thenReturn(null);
        doThrow(new RuntimeException("database unavailable")).when(userPersistService)
            .createUser(eq(AuthConstants.ANONYMOUS_USER), anyString());
        AnonymousAccessInitializer initializer = initializer(Runnable::run);
        assertDoesNotThrow(initializer::reconcile);
        assertDoesNotThrow(initializer::reconcile);
        verify(userPersistService, times(2)).createUser(eq(AuthConstants.ANONYMOUS_USER),
            anyString());
    }
    
    @Test
    void testMissingRoleAfterWriteRemainsRetryable() {
        enableAnonymousAccess();
        Page<RoleInfo> nullItems = rolePage(false);
        nullItems.setPageItems(null);
        when(rolePersistService.getRolesByUserNameAndRoleName(anyString(), anyString(),
            eq(1), eq(1))).thenReturn(nullItems, rolePage(false));
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER))
            .thenReturn(new User());
        Page<PermissionInfo> nullPermissions = permissionPage(false);
        nullPermissions.setPageItems(null);
        when(permissionPersistService.getPermissions(AuthConstants.ANONYMOUS_ROLE, 1,
            Integer.MAX_VALUE)).thenReturn(nullPermissions);
        assertDoesNotThrow(() -> initializer(Runnable::run).reconcile());
    }
    
    @Test
    void testUnconfirmedDuplicatePermissionAndRoleRemainRetryable() {
        enableAnonymousAccess();
        when(rolePersistService.getRolesByUserNameAndRoleName(anyString(), anyString(),
            eq(1), eq(1))).thenReturn(rolePage(false));
        when(userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER))
            .thenReturn(new User());
        when(permissionPersistService.getPermissions(AuthConstants.ANONYMOUS_ROLE, 1,
            Integer.MAX_VALUE)).thenReturn(permissionPage(false));
        doThrow(new RuntimeException("permission failure")).when(permissionPersistService)
            .addPermission(AuthConstants.ANONYMOUS_ROLE, "public:*:ai/*", "r");
        assertDoesNotThrow(() -> initializer(Runnable::run).reconcile());
        
        reset(permissionPersistService);
        when(permissionPersistService.getPermissions(AuthConstants.ANONYMOUS_ROLE, 1,
            Integer.MAX_VALUE)).thenReturn(permissionPage(true));
        when(rolePersistService.getRolesByUserNameAndRoleName(anyString(), anyString(),
            eq(1), eq(1))).thenReturn(rolePage(false));
        doThrow(new RuntimeException("role failure")).when(rolePersistService)
            .addRole(AuthConstants.ANONYMOUS_ROLE, AuthConstants.ANONYMOUS_USER);
        assertDoesNotThrow(() -> initializer(Runnable::run).reconcile());
    }
    
    @Test
    void testSchedulingFailureAndConcurrentReconcileAreIgnored() {
        enableAnonymousAccess();
        AnonymousAccessInitializer rejected = initializer(command -> {
            throw new IllegalStateException("rejected");
        });
        assertDoesNotThrow(rejected::requestReconcile);
        AnonymousAccessInitializer concurrent = initializer(Runnable::run);
        AtomicBoolean reconciling = (AtomicBoolean) ReflectionTestUtils.getField(concurrent,
            "reconciling");
        reconciling.set(true);
        concurrent.reconcile();
        verifyNoInteractions(userPersistService, rolePersistService, permissionPersistService);
    }
    
    private AnonymousAccessInitializer initializer(Executor executor) {
        return new AnonymousAccessInitializer(configProvider, userPersistService,
            rolePersistService, permissionPersistService, executor);
    }
    
    private void enableAnonymousAccess() {
        when(configProvider.getConfig()).thenReturn(config(true));
    }
    
    private NacosAuthPluginConfig config(boolean enabled) {
        Map<String, String> config = new LinkedHashMap<>();
        config.put(NacosAuthPluginConfig.ANONYMOUS_AI_ENABLED, Boolean.toString(enabled));
        return NacosAuthPluginConfig.from(config, false);
    }
    
    private Page<RoleInfo> rolePage(boolean ready) {
        RoleInfo role = new RoleInfo();
        role.setRole(ready ? AuthConstants.ANONYMOUS_ROLE : "other-role");
        role.setUsername(ready ? AuthConstants.ANONYMOUS_USER : "other-user");
        return page(role);
    }
    
    private Page<PermissionInfo> permissionPage(boolean ready) {
        PermissionInfo permission = new PermissionInfo();
        permission.setResource(ready ? "public:*:ai/*" : "other");
        permission.setAction(ready ? "r" : "w");
        return page(permission);
    }
    
    private <T> Page<T> page(T item) {
        Page<T> result = new Page<>();
        result.setPageItems(Collections.singletonList(item));
        return result;
    }
}
