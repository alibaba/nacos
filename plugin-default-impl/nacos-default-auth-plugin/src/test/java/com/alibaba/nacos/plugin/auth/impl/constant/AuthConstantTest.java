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

package com.alibaba.nacos.plugin.auth.impl.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthConstantTest {
    
    @Test
    void testAuthPageConstantConstructorAndValues() {
        assertNotNull(new AuthPageConstant());
        assertEquals("OFFSET", AuthPageConstant.OFFSET);
        assertEquals("OFFSET ? ROWS", AuthPageConstant.OFFSET_ROWS);
        assertEquals("FETCH NEXT ? ROWS ONLY", AuthPageConstant.FETCH_NEXT);
        assertEquals("LIMIT", AuthPageConstant.LIMIT);
        assertEquals("LIMIT ?,?", AuthPageConstant.LIMIT_SIZE);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    void testAuthConstantsConstructorAndValues() {
        assertNotNull(new AuthConstants());
        assertEquals("nacos", AuthConstants.AUTH_PLUGIN_TYPE);
        assertEquals("ldap", AuthConstants.LDAP_AUTH_PLUGIN_TYPE);
        assertEquals("ROLE_ADMIN", AuthConstants.GLOBAL_ADMIN_ROLE);
        assertEquals("/v3/auth/user", AuthConstants.USER_PATH);
        assertEquals("__nacos_anonymous__", AuthConstants.ANONYMOUS_USER);
        assertEquals("__nacos_anonymous_role__", AuthConstants.ANONYMOUS_ROLE);
    }
}
