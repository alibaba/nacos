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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityContextTest {
    
    private static final String TEST = "test";
    
    private IdentityContext identityContext;
    
    @BeforeEach
    void setUp() throws Exception {
        identityContext = new IdentityContext();
    }
    
    @Test
    void testGetParameter() {
        assertNull(identityContext.getParameter(TEST));
        identityContext.setParameter(TEST, TEST);
        assertEquals(TEST, identityContext.getParameter(TEST));
    }
    
    @Test
    void testGetParameterWithDefaultValue() {
        assertEquals(TEST, identityContext.getParameter(TEST, TEST));
        identityContext.setParameter(TEST, TEST + "new");
        assertEquals(TEST + "new", identityContext.getParameter(TEST, TEST));
        long actual = identityContext.getParameter(TEST, 1L);
        assertEquals(1L, actual);
    }
    
    @Test
    void testGetParameterWithNullDefaultValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            identityContext.getParameter(TEST, null);
        });
    }
    
    @Test
    void testContainsParameter() {
        assertFalse(identityContext.containsParameter(TEST));
        identityContext.setParameter(TEST, "");
        assertTrue(identityContext.containsParameter(TEST));
    }
    
    @Test
    void testRequestIdentityParameter() {
        identityContext.setRequestIdentityParameter("accessToken", "token");
        identityContext.setParameter("identity_id", "user");
        
        Set<String> requestIdentityNames = identityContext.getRequestIdentityNames();
        assertEquals(1, requestIdentityNames.size());
        assertTrue(requestIdentityNames.contains("accessToken"));
        assertEquals("token", identityContext.getParameter("accessToken"));
        assertFalse(requestIdentityNames.contains("identity_id"));
        assertThrows(UnsupportedOperationException.class,
            () -> requestIdentityNames.add("anotherIdentity"));
    }
}
