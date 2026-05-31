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
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.utils.RemoteServerUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NacosUserServiceRemoteImpl#getUser(String)} cache-access semantics.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NacosUserServiceRemoteImplTest {
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private NacosRestTemplate restTemplate;
    
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
        assertEquals(0, cache.containsKeyCount.get(),
            "fix must not consult containsKey separately");
    }
    
    @Test
    void testLoadUserByUsernameReadsCachedUser() throws Exception {
        NacosUserServiceRemoteImpl service = new NacosUserServiceRemoteImpl(authConfigs);
        User alice = new User();
        alice.setUsername("alice");
        alice.setPassword("pwd");
        CountingMap<String, User> cache = new CountingMap<>();
        cache.put("alice", alice);
        injectCachedUserMap(service, cache);
        
        UserDetails userDetails = service.loadUserByUsername("alice");
        
        assertEquals("alice", userDetails.getUsername());
    }
    
    @Test
    void testCacheMissReloadsUserAndLoadMissingUserThrows() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosUserServiceRemoteImpl service = newServiceWithRestTemplate();
        Page<User> page = new Page<>();
        User user = new User();
        user.setUsername("alice");
        user.setPassword("pwd");
        page.setPageItems(Collections.singletonList(user));
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenReturn(okJson(page));
        
        assertEquals("alice", service.getUser("alice").getUsername());
        
        reset(restTemplate);
        NacosUserServiceRemoteImpl missingService = newServiceWithRestTemplate();
        Page<User> emptyPage = new Page<>();
        emptyPage.setPageItems(Collections.emptyList());
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenReturn(okJson(emptyPage));
        
        assertThrows(UsernameNotFoundException.class,
            () -> missingService.loadUserByUsername("missing"));
    }
    
    @Test
    void testRemoteUserOperations() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosUserServiceRemoteImpl service = newServiceWithRestTemplate();
        Page<User> page = new Page<>();
        User user = new User();
        user.setUsername("alice");
        user.setPassword("pwd");
        page.setPageItems(Collections.singletonList(user));
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenReturn(okJson(page), okJson(page),
                okJson(Arrays.asList("alice", "admin")));
        when(
            restTemplate.<String>putForm(anyString(), any(Header.class), any(Query.class), anyMap(),
                eq(String.class)))
            .thenReturn(okText());
        when(restTemplate.<String>postForm(anyString(), any(Header.class), any(Query.class),
            anyMap(),
            eq(String.class))).thenReturn(okText());
        when(restTemplate.<String>delete(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenReturn(okText());
        
        assertEquals("alice", service.getUsers(1, 10, "alice").getPageItems().get(0)
            .getUsername());
        assertEquals("alice", service.findUsers("ali", 1, 10).getPageItems().get(0)
            .getUsername());
        assertEquals(Arrays.asList("alice", "admin"), service.findUserNames("a"));
        service.updateUserPassword("alice", "newPwd");
        service.createUser("alice", "pwd", true);
        service.createUser("nacos", "pwd", true);
        service.deleteUser("alice");
    }
    
    @Test
    void testRemoteUserOperationWrapsNacosException() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosUserServiceRemoteImpl service = newServiceWithRestTemplate();
        when(restTemplate.<String>putForm(anyString(), any(Header.class), any(Query.class),
            anyMap(),
            eq(String.class))).thenThrow(new NacosException(403, "denied"));
        
        NacosRuntimeException exception = assertThrows(NacosRuntimeException.class,
            () -> service.updateUserPassword("alice", "pwd"));
        
        assertEquals(403, exception.getErrCode());
    }
    
    @Test
    void testRemoteUserOperationWrapsUnexpectedException() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosUserServiceRemoteImpl service = newServiceWithRestTemplate();
        when(restTemplate.<String>delete(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        NacosRuntimeException exception = assertThrows(NacosRuntimeException.class,
            () -> service.deleteUser("alice"));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
    }
    
    @Test
    void testRemoteUserReadOperationsWrapExceptions() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosUserServiceRemoteImpl service = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new NacosException(403, "denied"));
        
        assertEquals(403, assertThrows(NacosRuntimeException.class,
            () -> service.findUserNames("alice")).getErrCode());
        
        reset(restTemplate);
        NacosUserServiceRemoteImpl getUsersService = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new NacosException(500, "denied"));
        
        assertEquals(500, assertThrows(NacosRuntimeException.class,
            () -> getUsersService.getUsers(1, 10, "alice")).getErrCode());
        
        reset(restTemplate);
        NacosUserServiceRemoteImpl findUsersService = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> findUsersService.findUsers("alice", 1, 10)).getErrCode());
        
        reset(restTemplate);
        NacosUserServiceRemoteImpl findUserNamesService = newServiceWithRestTemplate();
        when(restTemplate.<String>get(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> findUserNamesService.findUserNames("alice")).getErrCode());
    }
    
    @Test
    void testRemoteUserWriteOperationsWrapRemainingExceptions() throws Exception {
        prepareRemoteServer();
        prepareServerIdentity();
        NacosUserServiceRemoteImpl updateService = newServiceWithRestTemplate();
        when(restTemplate.<String>putForm(anyString(), any(Header.class), any(Query.class),
            anyMap(),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> updateService.updateUserPassword("alice", "pwd")).getErrCode());
        
        reset(restTemplate);
        NacosUserServiceRemoteImpl createService = newServiceWithRestTemplate();
        when(restTemplate.<String>postForm(anyString(), any(Header.class), any(Query.class),
            anyMap(),
            eq(String.class))).thenThrow(new NacosException(403, "denied"));
        
        assertEquals(403, assertThrows(NacosRuntimeException.class,
            () -> createService.createUser("alice", "pwd", true)).getErrCode());
        
        reset(restTemplate);
        NacosUserServiceRemoteImpl createUnexpectedService = newServiceWithRestTemplate();
        when(restTemplate.<String>postForm(anyString(), any(Header.class), any(Query.class),
            anyMap(),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> createUnexpectedService.createUser("alice", "pwd", true)).getErrCode());
        
        reset(restTemplate);
        NacosUserServiceRemoteImpl adminCreateService = newServiceWithRestTemplate();
        when(restTemplate.<String>postForm(anyString(), any(Header.class), any(Query.class),
            anyMap(),
            eq(String.class))).thenThrow(new NacosException(500, "denied"));
        
        assertEquals(500, assertThrows(NacosRuntimeException.class,
            () -> adminCreateService.createUser(AuthConstants.DEFAULT_USER, "pwd", true))
            .getErrCode());
        
        reset(restTemplate);
        NacosUserServiceRemoteImpl adminUnexpectedService = newServiceWithRestTemplate();
        when(restTemplate.<String>postForm(anyString(), any(Header.class), any(Query.class),
            anyMap(),
            eq(String.class))).thenThrow(new IllegalStateException("boom"));
        
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosRuntimeException.class,
            () -> adminUnexpectedService.createUser(AuthConstants.DEFAULT_USER, "pwd", true))
            .getErrCode());
        
        reset(restTemplate);
        NacosUserServiceRemoteImpl deleteService = newServiceWithRestTemplate();
        when(restTemplate.<String>delete(anyString(), any(Header.class), any(Query.class),
            eq(String.class))).thenThrow(new NacosException(403, "denied"));
        
        assertEquals(403, assertThrows(NacosRuntimeException.class,
            () -> deleteService.deleteUser("alice")).getErrCode());
    }
    
    private NacosUserServiceRemoteImpl newServiceWithRestTemplate() throws Exception {
        NacosUserServiceRemoteImpl service = new NacosUserServiceRemoteImpl(authConfigs);
        Field field = NacosUserServiceRemoteImpl.class.getDeclaredField("nacosRestTemplate");
        field.setAccessible(true);
        field.set(service, restTemplate);
        return service;
    }
    
    private void prepareServerIdentity() {
        lenient().when(authConfigs.getServerIdentityKey()).thenReturn("identity");
        lenient().when(authConfigs.getServerIdentityValue()).thenReturn("value");
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
    
    private static void injectCachedUserMap(NacosUserServiceRemoteImpl service,
        java.util.Map<String, User> map)
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
