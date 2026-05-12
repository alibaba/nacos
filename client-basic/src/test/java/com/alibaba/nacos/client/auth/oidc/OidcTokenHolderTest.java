/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.oidc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link OidcTokenHolder}.
 *
 * @author wangzji
 */
class OidcTokenHolderTest {
    
    private OidcTokenHolder tokenHolder;
    
    @BeforeEach
    void setUp() {
        tokenHolder = new OidcTokenHolder();
    }
    
    @Test
    void testInitialStateNeedsRefresh() {
        // Token holder starts with no token
        assertNull(tokenHolder.getAccessToken());
        assertTrue(tokenHolder.isExpiredOrNeedRefresh());
    }
    
    @Test
    void testFetchTokenWithNullEndpoint() {
        OidcClientContext context = new OidcClientContext();
        // context has no token endpoint
        boolean result = tokenHolder.fetchToken(context);
        assertFalse(result, "Should fail when token endpoint is null");
    }
    
    @Test
    void testGenerateTokenRefreshWindowWithZeroTtl() {
        long window = tokenHolder.generateTokenRefreshWindow(0);
        assertTrue(window == 0, "Window should be 0 for TTL 0");
    }
    
    @Test
    void testGenerateTokenRefreshWindowWithNegativeTtl() {
        long window = tokenHolder.generateTokenRefreshWindow(-1);
        assertTrue(window == 0, "Window should be 0 for negative TTL");
    }
    
    @Test
    void testGenerateTokenRefreshWindowWithNormalTtl() {
        // TTL = 300s => startNumber = 300/15 = 20, endNumber = 300/10 = 30
        long window = tokenHolder.generateTokenRefreshWindow(300);
        assertTrue(window >= 20 && window < 30,
            "Window should be in range [20, 30), got: " + window);
    }
    
    @Test
    void testGenerateTokenRefreshWindowWithSmallTtl() {
        // TTL = 10s => startNumber = 0, endNumber = 1
        long window = tokenHolder.generateTokenRefreshWindow(10);
        assertTrue(window >= 0 && window <= 1,
            "Window should be 0 or 1 for TTL 10, got: " + window);
    }
    
    @Test
    void testGenerateTokenRefreshWindowWithVerySmallTtl() {
        // TTL = 5s => startNumber = 0, endNumber = 0 => falls to startNumber
        long window = tokenHolder.generateTokenRefreshWindow(5);
        assertTrue(window == 0, "Window should be 0 for very small TTL, got: " + window);
    }
}
