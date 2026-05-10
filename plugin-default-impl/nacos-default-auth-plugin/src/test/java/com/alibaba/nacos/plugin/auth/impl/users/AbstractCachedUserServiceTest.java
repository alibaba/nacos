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
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AbstractCachedUserService} cache field semantics.
 */
class AbstractCachedUserServiceTest {
    
    @Test
    void testCachedUserMapFieldIsVolatile() throws NoSuchFieldException {
        // The reload() method publishes a freshly built map by reassigning the userMap field.
        // Without volatile, readers calling getCachedUserMap() are not guaranteed to observe
        // the new reference, which can cause stale auth lookups after user create/delete.
        Field field = AbstractCachedUserService.class.getDeclaredField("userMap");
        assertTrue(Modifier.isVolatile(field.getModifiers()),
            "userMap must be volatile so reload() reference swaps are visible to readers");
    }
    
    @Test
    void testReloadReplacesCachedUserMap() {
        TestableCachedUserService service = new TestableCachedUserService();
        Map<String, User> initial = service.getCachedUserMap();
        assertTrue(initial.isEmpty(), "userMap starts empty");
        
        User alice = newUser("alice");
        service.setNextPage(pageOf(alice));
        service.reload();
        
        Map<String, User> afterFirstReload = service.getCachedUserMap();
        assertNotSame(initial, afterFirstReload, "reload() must swap in a new map instance");
        assertEquals(1, afterFirstReload.size());
        assertSame(alice, afterFirstReload.get("alice"));
    }
    
    @Test
    void testReloadKeepsExistingMapWhenSourceReturnsNull() {
        TestableCachedUserService service = new TestableCachedUserService();
        service.setNextPage(pageOf(newUser("bob")));
        service.reload();
        Map<String, User> afterFirstReload = service.getCachedUserMap();
        
        service.setNextPage(null);
        service.reload();
        
        assertSame(afterFirstReload, service.getCachedUserMap(),
            "null page must not replace the previously loaded cache");
    }
    
    @Test
    void testReloadSwallowsErrorsAndKeepsExistingMap() {
        TestableCachedUserService service = new TestableCachedUserService();
        service.setNextPage(pageOf(newUser("carol")));
        service.reload();
        Map<String, User> afterFirstReload = service.getCachedUserMap();
        
        service.setNextException(new RuntimeException("boom"));
        service.reload();
        
        assertSame(afterFirstReload, service.getCachedUserMap(),
            "an exception during reload must not blank out the cache");
    }
    
    private static User newUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("pwd-" + username);
        return user;
    }
    
    private static Page<User> pageOf(User user) {
        Page<User> page = new Page<>();
        page.setPageItems(Collections.singletonList(user));
        return page;
    }
    
    /**
     * Minimal concrete subclass that exposes only the inherited {@link AbstractCachedUserService#reload()} hook
     * with a controllable {@link #getUsers(int, int, String)} response.
     */
    private static final class TestableCachedUserService extends AbstractCachedUserService {
        
        private Page<User> nextPage;
        
        private RuntimeException nextException;
        
        void setNextPage(Page<User> nextPage) {
            this.nextPage = nextPage;
            this.nextException = null;
        }
        
        void setNextException(RuntimeException nextException) {
            this.nextException = nextException;
            this.nextPage = null;
        }
        
        @Override
        public Page<User> getUsers(int pageNo, int pageSize, String username) {
            if (nextException != null) {
                throw nextException;
            }
            return nextPage;
        }
        
        @Override
        public void updateUserPassword(String username, String password) {
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
            return Collections.emptyList();
        }
        
        @Override
        public void createUser(String username, String password, boolean encode) {
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
