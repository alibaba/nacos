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

package com.alibaba.nacos.auth.context;

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class HtppIdentityContextBuilderTest {
    
    private static final String TEST_PLUGIN = "test";
    
    private static final String IDENTITY_TEST_KEY = "identity-test-key";
    
    private static final String IDENTITY_TEST_VALUE = "identity-test-value";
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private Enumeration<String> headerNames;
    
    @Mock
    private Enumeration<String> parameterNames;
    
    private HttpIdentityContextBuilder identityContextBuilder;
    
    @BeforeEach
    void setUp() throws Exception {
        identityContextBuilder = new HttpIdentityContextBuilder(authConfigs);
        when(authConfigs.getNacosAuthSystemType()).thenReturn(TEST_PLUGIN);
    }
    
    @Test
    void testBuildWithoutPlugin() {
        mockHeader(true);
        mockParameter(true);
        when(authConfigs.getNacosAuthSystemType()).thenReturn("non-exist");
        IdentityContext actual = identityContextBuilder.build(request);
        assertNull(actual.getParameter(IDENTITY_TEST_KEY));
    }
    
    @Test
    void testBuildWithHeader() {
        mockHeader(true);
        mockParameter(false);
        IdentityContext actual = identityContextBuilder.build(request);
        assertEquals(IDENTITY_TEST_VALUE, actual.getParameter(IDENTITY_TEST_KEY));
        assertEquals("1.1.1.1", actual.getParameter(Constants.Identity.REMOTE_IP));
    }
    
    @Test
    void testBuildWithParameter() {
        mockHeader(false);
        mockParameter(true);
        IdentityContext actual = identityContextBuilder.build(request);
        assertEquals(IDENTITY_TEST_VALUE, actual.getParameter(IDENTITY_TEST_KEY));
    }
    
    private void mockHeader(boolean contained) {
        when(request.getHeaderNames()).thenReturn(headerNames);
        if (contained) {
            when(headerNames.hasMoreElements()).thenReturn(true, false);
            when(headerNames.nextElement()).thenReturn(IDENTITY_TEST_KEY, (String) null);
            when(request.getHeader(IDENTITY_TEST_KEY)).thenReturn(IDENTITY_TEST_VALUE);
            when(request.getHeader(Constants.Identity.X_REAL_IP)).thenReturn("1.1.1.1");
        }
    }
    
    private void mockParameter(boolean contained) {
        when(request.getParameterNames()).thenReturn(parameterNames);
        if (contained) {
            when(parameterNames.hasMoreElements()).thenReturn(true, false);
            when(parameterNames.nextElement()).thenReturn(IDENTITY_TEST_KEY, (String) null);
            when(request.getParameter(IDENTITY_TEST_KEY)).thenReturn(IDENTITY_TEST_VALUE);
        }
    }
}
