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

package com.alibaba.nacos.plugin.auth.impl.authenticate;

import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MissingLdapAuthenticationManagerTest {
    
    private final MissingLdapAuthenticationManager manager =
        new MissingLdapAuthenticationManager("missing dependency");
    
    @Test
    void testAuthenticateWithUsernamePassword() {
        assertThrows(AccessException.class, () -> manager.authenticate("nacos", "nacos"));
    }
    
    @Test
    void testAuthenticateWithToken() {
        assertThrows(AccessException.class, () -> manager.authenticate("token"));
    }
    
    @Test
    void testAuthenticateWithRequest() {
        assertThrows(AccessException.class,
            () -> manager.authenticate((jakarta.servlet.http.HttpServletRequest) null));
    }
    
    @Test
    void testAuthorize() {
        assertThrows(AccessException.class,
            () -> manager.authorize((Permission) null, new NacosUser("nacos")));
    }
    
    @Test
    void testHasGlobalAdminRole() {
        assertFalse(manager.hasGlobalAdminRole());
        assertFalse(manager.hasGlobalAdminRole("nacos"));
        assertFalse(manager.hasGlobalAdminRole(new NacosUser("nacos")));
    }
}
