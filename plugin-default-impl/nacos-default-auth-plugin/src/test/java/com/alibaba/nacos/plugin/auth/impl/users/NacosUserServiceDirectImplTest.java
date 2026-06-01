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

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NacosUserServiceDirectImplTest.
 *
 * @author FangYuan on: 2025-07-24 16:00:47
 */
@ExtendWith(MockitoExtension.class)
class NacosUserServiceDirectImplTest {
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private UserPersistService userPersistService;
    
    private NacosUserServiceDirectImpl nacosUserService;
    
    @BeforeEach
    void setUp() {
        nacosUserService = new NacosUserServiceDirectImpl(authConfigs, userPersistService);
    }
    
    @Test
    void testCreateUserWithBlankUsername() {
        String blankUsername = "";
        String password = "testPassword";
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> nacosUserService.createUser(blankUsername, password));
        
        assertEquals("username is blank", exception.getMessage());
        verify(userPersistService, never()).createUser(anyString(), anyString());
    }
    
    @Test
    void testCreateUserWithBlankPassword() {
        String username = "testUser";
        String blankPassword = "";
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> nacosUserService.createUser(username, blankPassword));
        
        assertEquals("password is blank", exception.getMessage());
        verify(userPersistService, never()).createUser(anyString(), anyString());
    }
    
    @Test
    void testCreateUserWithReservedAnonymousUsername() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> nacosUserService.createUser("__nacos_anonymous__", "password"));
        
        assertTrue(exception.getMessage().contains("reserved by the system"));
        verify(userPersistService, never()).createUser(anyString(), anyString());
    }
    
    @Test
    void testDeleteUserWithReservedAnonymousUsername() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> nacosUserService.deleteUser("__nacos_anonymous__"));
        
        assertTrue(exception.getMessage().contains("reserved by the system"));
        verify(userPersistService, never()).deleteUser(anyString());
    }
    
    @Test
    void testLoadUserByUsernameWhenCachingDisabled() {
        User user = new User();
        user.setUsername("nacos");
        user.setPassword("pwd");
        when(authConfigs.isCachingEnabled()).thenReturn(false);
        when(userPersistService.findUserByUsername("nacos")).thenReturn(user);
        
        UserDetails userDetails = nacosUserService.loadUserByUsername("nacos");
        
        assertEquals("nacos", userDetails.getUsername());
        assertEquals("pwd", userDetails.getPassword());
    }
    
    @Test
    void testLoadUserByUsernameThrowsWhenUserMissing() {
        when(authConfigs.isCachingEnabled()).thenReturn(false);
        when(userPersistService.findUserByUsername("missing")).thenReturn(null);
        
        assertThrows(UsernameNotFoundException.class,
            () -> nacosUserService.loadUserByUsername("missing"));
    }
    
    @Test
    void testDelegateQueriesToPersistService() {
        Page<User> page = new Page<>();
        when(userPersistService.getUsers(1, 10, "nacos")).thenReturn(page);
        when(userPersistService.findUsersLike4Page("na", 1, 10)).thenReturn(page);
        when(userPersistService.findUserLikeUsername("na"))
            .thenReturn(Collections.singletonList("nacos"));
        User user = new User();
        when(userPersistService.findUserByUsername("nacos")).thenReturn(user);
        
        assertSame(page, nacosUserService.getUsers(1, 10, "nacos"));
        assertSame(page, nacosUserService.findUsers("na", 1, 10));
        assertEquals(Collections.singletonList("nacos"), nacosUserService.findUserNames("na"));
        assertSame(user, nacosUserService.getUser("nacos"));
    }
    
    @Test
    void testUpdateCreateAndDeleteUser() {
        nacosUserService.updateUserPassword("nacos", "pwd");
        nacosUserService.createUser("plain", "pwd", false);
        nacosUserService.createUser("encoded", "pwd", true);
        nacosUserService.deleteUser("plain");
        
        verify(userPersistService).updateUserPassword(eq("nacos"), anyString());
        verify(userPersistService).createUser("plain", "pwd");
        ArgumentCaptor<String> encodedPasswordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userPersistService).createUser(eq("encoded"), encodedPasswordCaptor.capture());
        assertTrue(encodedPasswordCaptor.getValue().startsWith("$2"));
        verify(userPersistService).deleteUser("plain");
    }
}
