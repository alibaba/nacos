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

package com.alibaba.nacos.console.filter;

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityResult;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosConsoleAuthFilterTest {
    
    @Mock
    private NacosAuthConfig authConfig;
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    NacosConsoleAuthFilter consoleAuthFilter;
    
    @BeforeEach
    void setUp() {
        consoleAuthFilter = new NacosConsoleAuthFilter(authConfig, methodsCache);
    }
    
    @Test
    void isAuthEnabled() {
        assertFalse(consoleAuthFilter.isAuthEnabled());
        when(consoleAuthFilter.isAuthEnabled()).thenReturn(true);
        assertTrue(consoleAuthFilter.isAuthEnabled());
    }
    
    @Test
    void checkServerIdentity() {
        assertEquals(ServerIdentityResult.noMatched().getStatus(),
                consoleAuthFilter.checkServerIdentity(null, null).getStatus());
    }
}