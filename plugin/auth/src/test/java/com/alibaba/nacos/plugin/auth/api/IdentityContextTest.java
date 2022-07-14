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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IdentityContextTest {
    
    private static final String TEST = "test";
    
    private IdentityContext identityContext;
    
    @Before
    public void setUp() throws Exception {
        identityContext = new IdentityContext();
    }
    
    @Test
    public void testGetParameter() {
        assertNull(identityContext.getParameter(TEST));
        identityContext.setParameter(TEST, TEST);
        assertEquals(TEST, identityContext.getParameter(TEST));
    }
    
    @Test
    public void testGetParameterWithDefaultValue() {
        assertEquals(TEST, identityContext.getParameter(TEST, TEST));
        identityContext.setParameter(TEST, TEST + "new");
        assertEquals(TEST + "new", identityContext.getParameter(TEST, TEST));
        long actual = identityContext.getParameter(TEST, 1L);
        assertEquals(1L, actual);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetParameterWithNullDefaultValue() {
        identityContext.getParameter(TEST, null);
    }
}
