/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.utils.RemoteServerUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for cache-access semantics on {@link NacosRoleServiceRemoteImpl#getPermissions(String)}
 * and {@link NacosRoleServiceRemoteImpl#getRoles(String)}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NacosRoleServiceRemoteImplTest {
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private NacosRestTemplate restTemplate;
    
    @BeforeEach
    void setUp() {
        if (EnvUtil.getEnvironment() == null) {
            EnvUtil.setEnvironment(new MockEnvironment());
        }
    }
    
    @Test
    void testGetPermissionsReadsCachedMapOnceOnHit() throws Exception {
        // Reproduces the same TOCTOU pattern as getUser/getRoles: previously
        // containsKey + get were issued through two separate getCached* calls,
        // which let the scheduled reload swap the map reference between them and
        // produce inconsistent observations. After the fix the map is read once.
        NacosRoleServiceRemoteImpl service = new NacosRoleServiceRemoteImpl(authConfigs);
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.setRole("admin");
        permissionInfo.setResource("ns:*:*");
        permissionInfo.setAction("rw");
        List<PermissionInfo> permissions = Collections.singletonList(permissionInfo);
        CountingMap<String, List<PermissionInfo>> cache = new CountingMap<>();
        cache.put("admin", permissions);
        injectField("permissionInfoMap", service, cache);
        
        List<PermissionInfo> result = service.getPermissions("admin");
        
        assertSame(permissions, result, "cache hit must return the cached permission list");
        assertEquals(1, cache.getCount.get(), "cache hit must read the map exactly once");
        assertEquals(0, cache.containsKeyCount.get(),
            "fix must not consult containsKey separately");
    }
    
    @Test
    void testGetRolesReadsCachedMapOnceOnHit() throws Exception {
        NacosRoleServiceRemoteImpl service = new NacosRoleServiceRemoteImpl(authConfigs);
        RoleInfo roleInfo = new RoleInfo();
        roleInfo.setRole("admin");
        roleInfo.setUsername("alice");
        List<RoleInfo> roles = Collections.singletonList(roleInfo);
        CountingMap<String, List<RoleInfo>> cache = new CountingMap<>();
        cache.put("alice", roles);
        injectField("roleInfoMap", service, cache);
        
        List<RoleInfo> result = service.getRoles("alice");
        
        assertSame(roles, result, "cache hit must return the cached role list");
        assertEquals(1, cache.getCount.get(), "cache hit must read the map exactly once");
        assertEquals(0, cache.containsKeyCount.get(),
            "fix must not consult containsKey separately");
    }
    
    @Test
    void testRemoteRoleAndPermissionOperations() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosRoleServiceRemoteImpl service = newServiceWithRestTemplate();
        Page<PermissionInfo> permissionPage = new Page<>();
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.setRole("admin");
        permissionInfo.setResource("public:*:*");
        permissionInfo.setAction("rw");
        permissionPage.setPageItems(Collections.singletonList(permissionInfo));
        Page<RoleInfo> rolePage = new Page<>();
        RoleInfo roleInfo = new RoleInfo();
        roleInfo.setRole("admin");
        roleInfo.setUsername("alice");
        rolePage.setPageItems(Collections.singletonList(roleInfo));
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenReturn(okJson(permissionPage), okJson(permissionPage),
                okJson(rolePage), okJson(rolePage), okJson(Arrays.asList("admin", "developer")),
                okJson(rolePage));
        when(restTemplate.<String>postForm(anyString(), any(Header.class), nullable(Query.class),
            anyMap(),
            eq(String.class))).thenReturn(okText());
        when(restTemplate.<String>postForm(anyString(), any(Header.class), anyMap(),
            eq(String.class))).thenReturn(okText());
        when(restTemplate.<String>delete(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenReturn(okText());
        
        assertEquals("admin", service.getPermissions("admin", 1, 10).getPageItems().get(0)
            .getRole());
        assertEquals("admin", service.findPermissions("adm", 1, 10).getPageItems().get(0)
            .getRole());
        assertEquals("alice", service.getRoles("alice", "admin", 1, 10).getPageItems().get(0)
            .getUsername());
        assertEquals("alice", service.findRoles("ali", "adm", 1, 10).getPageItems().get(0)
            .getUsername());
        assertEquals(Arrays.asList("admin", "developer"), service.findRoleNames("adm"));
        assertEquals("alice", service.getAllRoles().get(0).getUsername());
        service.addPermission("admin", "public:*:*", "rw");
        service.deletePermission("admin", "public:*:*", "rw");
        service.addRole("developer", "alice");
        service.deleteRole("developer", "alice");
        service.deleteRole("developer");
    }
    
    @Test
    void testAddAdminRoleUpdatesLocalCache() throws Exception {
        prepareRemoteServer();
        NacosRoleServiceRemoteImpl service = newServiceWithRestTemplate();
        when(authConfigs.isHasGlobalAdminRole()).thenReturn(false);
        Page<RoleInfo> rolePage = new Page<>();
        rolePage.setPageItems(Collections.emptyList());
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenReturn(okJson(rolePage));
        
        service.addAdminRole("nacos");
        
        assertTrue(getCachedRoleSet(service).contains(AuthConstants.GLOBAL_ADMIN_ROLE));
        verify(authConfigs).setHasGlobalAdminRole(true);
    }
    
    @Test
    void testAddAdminRoleSkipsWhenAdminAlreadyExists() throws Exception {
        prepareRemoteServer();
        NacosRoleServiceRemoteImpl service = newServiceWithRestTemplate();
        when(authConfigs.isHasGlobalAdminRole()).thenReturn(true);
        
        service.addAdminRole("nacos");
        
        verify(authConfigs).isHasGlobalAdminRole();
    }
    
    @Test
    void testCacheMissReloadsRolesAndPermissions() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosRoleServiceRemoteImpl roleService = newServiceWithRestTemplate();
        RoleInfo roleInfo = new RoleInfo();
        roleInfo.setRole("admin");
        roleInfo.setUsername("alice");
        Page<RoleInfo> rolePage = new Page<>();
        rolePage.setPageItems(Collections.singletonList(roleInfo));
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.setRole("admin");
        permissionInfo.setResource("public:*:*");
        permissionInfo.setAction("rw");
        Page<PermissionInfo> permissionPage = new Page<>();
        permissionPage.setPageItems(Collections.singletonList(permissionInfo));
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenReturn(okJson(rolePage), okJson(permissionPage));
        
        assertEquals("admin", roleService.getRoles("alice").get(0).getRole());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl permissionService = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenReturn(okJson(rolePage), okJson(permissionPage));
        
        assertEquals("public:*:*", permissionService.getPermissions("admin").get(0).getResource());
    }
    
    @Test
    void testAddRoleRejectsReservedRoles() throws Exception {
        prepareRemoteServer();
        NacosRoleServiceRemoteImpl service = newServiceWithRestTemplate();
        
        assertThrows(IllegalArgumentException.class,
            () -> service.addRole(AuthConstants.GLOBAL_ADMIN_ROLE, "alice"));
        assertThrows(IllegalArgumentException.class,
            () -> service.addRole(AuthConstants.ANONYMOUS_ROLE, "alice"));
    }
    
    @Test
    void testRemoteRoleOperationWrapsNacosException() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosRoleServiceRemoteImpl service = newServiceWithRestTemplate();
        when(restTemplate.<String>postForm(anyString(), any(Header.class), nullable(Query.class),
            anyMap(),
            eq(String.class))).thenThrow(new NacosException(403, "denied"));
        
        NacosRuntimeException exception = assertThrows(NacosRuntimeException.class,
            () -> service.addPermission("admin", "public:*:*", "rw"));
        
        assertEquals(403, exception.getErrCode());
    }
    
    @Test
    void testRemoteRoleOperationWrapsUnexpectedException() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosRoleServiceRemoteImpl service = newServiceWithRestTemplate();
        when(restTemplate.<String>delete(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        NacosRuntimeException exception = assertThrows(NacosRuntimeException.class,
            () -> service.deletePermission("admin", "public:*:*", "rw"));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
    }
    
    @Test
    void testRemoteRoleReadOperationsWrapExceptions() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosRoleServiceRemoteImpl service = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new NacosException(500, "denied"));
        
        assertEquals(500, assertThrows(NacosRuntimeException.class,
            () -> service.findRoleNames("admin")).getErrCode());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl rolePageService = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> rolePageService.getRoles("alice", "admin", 1, 10)).getErrCode());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl permissionPageService = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new NacosException(403, "denied"));
        
        assertEquals(403, assertThrows(NacosRuntimeException.class,
            () -> permissionPageService.getPermissions("admin", 1, 10)).getErrCode());
    }
    
    @Test
    void testRemoteRoleWriteOperationsWrapRemainingExceptions() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosRoleServiceRemoteImpl service = newServiceWithRestTemplate();
        when(restTemplate.<String>postForm(anyString(), any(Header.class), nullable(Query.class),
            anyMap(),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> service.addPermission("admin", "public:*:*", "rw")).getErrCode());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl deletePermissionService = newServiceWithRestTemplate();
        when(restTemplate.<String>delete(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new NacosException(403, "denied"));
        
        assertEquals(403, assertThrows(NacosRuntimeException.class,
            () -> deletePermissionService.deletePermission("admin", "public:*:*", "rw"))
            .getErrCode());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl addRoleService = newServiceWithRestTemplate();
        when(restTemplate.<String>postForm(anyString(), any(Header.class), anyMap(),
            eq(String.class))).thenThrow(new NacosException(500, "denied"));
        
        assertEquals(500, assertThrows(NacosRuntimeException.class,
            () -> addRoleService.addRole("developer", "alice")).getErrCode());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl deleteRoleService = newServiceWithRestTemplate();
        when(restTemplate.<String>delete(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new NacosException(500, "denied"))
            .thenThrow(new IllegalStateException("boom"));
        
        assertEquals(500, assertThrows(NacosRuntimeException.class,
            () -> deleteRoleService.deleteRole("developer", "alice")).getErrCode());
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> deleteRoleService.deleteRole("developer")).getErrCode());
    }
    
    @Test
    void testRemoteRoleOperationsWrapAdditionalExceptionBranches() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosRoleServiceRemoteImpl findNamesService = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> findNamesService.findRoleNames("admin")).getErrCode());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl addRoleService = newServiceWithRestTemplate();
        when(restTemplate.<String>postForm(anyString(), any(Header.class), anyMap(),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> addRoleService.addRole("developer", "alice")).getErrCode());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl deleteUserRoleService = newServiceWithRestTemplate();
        when(restTemplate.<String>delete(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> deleteUserRoleService.deleteRole("developer", "alice")).getErrCode());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl deleteRoleService = newServiceWithRestTemplate();
        when(restTemplate.<String>delete(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new NacosException(500, "denied"));
        
        assertEquals(500, assertThrows(NacosRuntimeException.class,
            () -> deleteRoleService.deleteRole("developer")).getErrCode());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl permissionPageService = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> permissionPageService.getPermissions("admin", 1, 10)).getErrCode());
        
        reset(restTemplate);
        NacosRoleServiceRemoteImpl rolePageService = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new NacosException(500, "denied"));
        
        assertEquals(500, assertThrows(NacosRuntimeException.class,
            () -> rolePageService.getRoles("alice", "admin", 1, 10)).getErrCode());
    }
    
    private NacosRoleServiceRemoteImpl newServiceWithRestTemplate() throws Exception {
        NacosRoleServiceRemoteImpl service = new NacosRoleServiceRemoteImpl(authConfigs);
        Field field = NacosRoleServiceRemoteImpl.class.getDeclaredField("nacosRestTemplate");
        field.setAccessible(true);
        field.set(service, restTemplate);
        return service;
    }
    
    private void prepareServerIdentity() {
        lenient().when(authConfigs.getServerIdentityKey()).thenReturn("identity");
        lenient().when(authConfigs.getServerIdentityValue()).thenReturn("value");
    }
    
    @SuppressWarnings("unchecked")
    private static java.util.Set<String> getCachedRoleSet(NacosRoleServiceRemoteImpl service)
        throws Exception {
        Field field = AbstractCachedRoleService.class.getDeclaredField("roleSet");
        field.setAccessible(true);
        return (java.util.Set<String>) field.get(service);
    }
    
    private static HttpRestResult<String> okText() {
        return new HttpRestResult<>(Header.newInstance(), 200, "ok", "success");
    }
    
    private static HttpRestResult<String> okJson(Object data) {
        return new HttpRestResult<>(Header.newInstance(), 200,
            JacksonUtils.toJson(Result.success(data)), "success");
    }
    
    private static void prepareRemoteServer() throws Exception {
        setRemoteServerUtilField("serverAddresses", Collections.singletonList("127.0.0.1:8848"));
        setRemoteServerUtilField("index", new AtomicInteger());
        setRemoteServerUtilField("remoteServerContextPath", "/nacos");
    }
    
    private static void setRemoteServerUtilField(String fieldName, Object value) throws Exception {
        Field field = RemoteServerUtil.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
    
    private static void injectField(String fieldName, NacosRoleServiceRemoteImpl service,
        Map<String, ?> map)
        throws Exception {
        Field field = AbstractCachedRoleService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, map);
    }
    
    private static final class CountingMap<K, V> extends ConcurrentHashMap<K, V> {
        
        private static final long serialVersionUID = 1L;
        
        final AtomicInteger getCount = new AtomicInteger();
        
        final AtomicInteger containsKeyCount = new AtomicInteger();
        
        @Override
        public V get(Object key) {
            getCount.incrementAndGet();
            return super.get(key);
        }
        
        @Override
        public boolean containsKey(Object key) {
            containsKeyCount.incrementAndGet();
            return super.containsKey(key);
        }
    }
}
