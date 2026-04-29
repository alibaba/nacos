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

package com.alibaba.nacos.plugin.auth.impl.users;

import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link NacosUserServiceRemoteImpl#getUser(String)} cache-access semantics.
 */
@ExtendWith(MockitoExtension.class)
class NacosUserServiceRemoteImplTest {

    @Mock
    private AuthConfigs authConfigs;

    @Test
    void testGetUserReadsCachedMapOnceOnHit() throws Exception {
        // Reproduces the TOCTOU pattern where getUser previously called containsKey
        // and then get on getCachedUserMap() — two separate field reads. Between the
        // two, the scheduled reload could swap the map reference, so containsKey
        // could observe true while get on the new snapshot returned null. The fix
        // reads the map once and inspects the value, guaranteeing a consistent view.
        NacosUserServiceRemoteImpl service = new NacosUserServiceRemoteImpl(authConfigs);
        User alice = new User();
        alice.setUsername("alice");
        alice.setPassword("pwd");
        CountingMap<String, User> cache = new CountingMap<>();
        cache.put("alice", alice);
        injectCachedUserMap(service, cache);

        User result = service.getUser("alice");

        assertSame(alice, result, "cache hit must return the cached user");
        assertEquals(1, cache.getCount.get(), "cache hit must read the map exactly once");
        assertEquals(0, cache.containsKeyCount.get(), "fix must not consult containsKey separately");
    }

    private static void injectCachedUserMap(NacosUserServiceRemoteImpl service, java.util.Map<String, User> map)
            throws Exception {
        Field field = AbstractCachedUserService.class.getDeclaredField("userMap");
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
