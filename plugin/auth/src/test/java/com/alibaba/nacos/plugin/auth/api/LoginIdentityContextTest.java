/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginIdentityContextTest {
    
    private static final String TEST = "test";
    
    private LoginIdentityContext loginIdentityContext;
    
    @BeforeEach
    void setUp() throws Exception {
        loginIdentityContext = new LoginIdentityContext();
    }
    
    @Test
    void testSetParameter() {
        assertNull(loginIdentityContext.getParameter(TEST));
        assertTrue(loginIdentityContext.getAllKey().isEmpty());
        loginIdentityContext.setParameter(TEST, TEST);
        assertEquals(TEST, loginIdentityContext.getParameter(TEST));
        assertEquals(1, loginIdentityContext.getAllKey().size());
    }
    
    @Test
    void testSetParameters() {
        assertNull(loginIdentityContext.getParameter(TEST));
        assertTrue(loginIdentityContext.getAllKey().isEmpty());
        Map<String, String> map = new HashMap<>(2);
        map.put(TEST, TEST);
        map.put(TEST + "2", TEST);
        loginIdentityContext.setParameters(map);
        assertEquals(TEST, loginIdentityContext.getParameter(TEST));
        assertEquals(TEST, loginIdentityContext.getParameter(TEST + "2"));
        assertEquals(2, loginIdentityContext.getAllKey().size());
    }
}
