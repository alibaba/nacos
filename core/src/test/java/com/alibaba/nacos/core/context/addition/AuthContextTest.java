/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.context.addition;

import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthContextTest {
    
    AuthContext authContext;
    
    @BeforeEach
    void setUp() {
        authContext = new AuthContext();
    }
    
    @Test
    void testSetIdentityContext() {
        IdentityContext identityContext = new IdentityContext();
        assertNull(authContext.getIdentityContext());
        authContext.setIdentityContext(identityContext);
        assertSame(identityContext, authContext.getIdentityContext());
    }
    
    @Test
    void testSetResource() {
        Resource resource = new Resource("", "", "", "", new Properties());
        assertNull(authContext.getResource());
        authContext.setResource(resource);
        assertSame(resource, authContext.getResource());
    }
    
    @Test
    void testSetAuthResult() {
        assertNull(authContext.getAuthResult());
        authContext.setAuthResult(true);
        assertTrue((boolean) authContext.getAuthResult());
    }
}