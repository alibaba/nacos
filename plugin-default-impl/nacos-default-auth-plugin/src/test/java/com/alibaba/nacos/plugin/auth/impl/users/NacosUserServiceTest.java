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

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosUserServiceTest {
    
    @Test
    void testDefaultCreateUserEncodesPassword() {
        TestNacosUserService userService = new TestNacosUserService();
        
        userService.createUser("nacos", "password");
        
        assertEquals("nacos", userService.username);
        assertEquals("password", userService.password);
        assertTrue(userService.encode);
    }
    
    private static class TestNacosUserService implements NacosUserService {
        
        private String username;
        
        private String password;
        
        private boolean encode;
        
        @Override
        public void updateUserPassword(String username, String password) {
        }
        
        @Override
        public Page<User> getUsers(int pageNo, int pageSize, String username) {
            return null;
        }
        
        @Override
        public Page<User> findUsers(String username, int pageNo, int pageSize) {
            return null;
        }
        
        @Override
        public User getUser(String username) {
            return null;
        }
        
        @Override
        public List<String> findUserNames(String username) {
            return null;
        }
        
        @Override
        public void createUser(String username, String password, boolean encode) {
            this.username = username;
            this.password = password;
            this.encode = encode;
        }
        
        @Override
        public void deleteUser(String username) {
        }
        
        @Override
        public UserDetails loadUserByUsername(String username) {
            return null;
        }
    }
}
