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

package com.alibaba.nacos.plugin.auth.impl.oidc.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcWebSecurityConfigTest {
    
    @Test
    void testOidcSecurityFilterChainBuildsHttpSecurity() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.build()).thenReturn(chain);
        
        SecurityFilterChain actual = new OidcWebSecurityConfig().oidcSecurityFilterChain(http);
        
        assertSame(chain, actual);
    }
}
