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

package com.alibaba.nacos.plugin.auth.impl.users;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosUserModelTest {
    
    @Test
    void testConstructorsAndAccessors() {
        NacosUser empty = new NacosUser();
        assertFalse(empty.isGlobalAdmin());
        
        NacosUser named = new NacosUser("nacos");
        assertEquals("nacos", named.getUserName());
        
        NacosUser user = new NacosUser("admin", "token");
        user.setGlobalAdmin(true);
        user.setToken("newToken");
        
        assertEquals("admin", user.getUserName());
        assertEquals("newToken", user.getToken());
        assertTrue(user.isGlobalAdmin());
        assertEquals("NacosUser{token='newToken', globalAdmin=true}", user.toString());
    }
    
    @Test
    void testNacosUserDetails() {
        com.alibaba.nacos.plugin.auth.impl.persistence.User user =
            new com.alibaba.nacos.plugin.auth.impl.persistence.User();
        user.setUsername("nacos");
        user.setPassword("pwd");
        NacosUserDetails userDetails = new NacosUserDetails(user);
        
        assertEquals("nacos", userDetails.getUsername());
        assertEquals("pwd", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().isEmpty());
        assertTrue(userDetails.isAccountNonExpired());
        assertTrue(userDetails.isAccountNonLocked());
        assertTrue(userDetails.isCredentialsNonExpired());
        assertTrue(userDetails.isEnabled());
    }
}
