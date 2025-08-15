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
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
}
