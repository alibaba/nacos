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

import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for cache-access semantics on {@link NacosRoleServiceRemoteImpl#getPermissions(String)}
 * and {@link NacosRoleServiceRemoteImpl#getRoles(String)}.
 */
@ExtendWith(MockitoExtension.class)
class NacosRoleServiceRemoteImplTest {
    
    @Mock
    private AuthConfigs authConfigs;
    
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
