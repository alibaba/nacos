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

import com.alibaba.nacos.auth.AuthPluginManager;
import com.alibaba.nacos.auth.AuthPluginService;
import com.alibaba.nacos.auth.api.IdentityContext;
import com.alibaba.nacos.auth.common.AuthConfigs;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HtppIdentityContextBuilderTest {
    
    private static final String TEST_PLUGIN = "test";
    
    private static final String IDENTITY_TEST_KEY = "identity-test-key";
    
    private static final String IDENTITY_TEST_VALUE = "identity-test-value";
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private AuthPluginService authPluginService;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private Enumeration<String> headerNames;
    
    @Mock
    private Enumeration<String> parameterNames;
    
    private HttpIdentityContextBuilder identityContextBuilder;
    
    @Before
    public void setUp() throws Exception {
        identityContextBuilder = new HttpIdentityContextBuilder(authConfigs);
        Field authServiceMapField = AuthPluginManager.class.getDeclaredField("authServiceMap");
        authServiceMapField.setAccessible(true);
        Map<String, AuthPluginService> authServiceMap = (Map<String, AuthPluginService>) authServiceMapField
                .get(AuthPluginManager.getInstance());
        authServiceMap.put(TEST_PLUGIN, authPluginService);
        when(authConfigs.getNacosAuthSystemType()).thenReturn(TEST_PLUGIN);
        when(authPluginService.identityNames()).thenReturn(Collections.singletonList(IDENTITY_TEST_KEY));
    }
    
    @Test
    public void testBuildWithoutPlugin() {
        mockHeader(true);
        mockParameter(true);
        when(authConfigs.getNacosAuthSystemType()).thenReturn("non-exist");
        IdentityContext actual = identityContextBuilder.build(request);
        assertNull(actual.getParameter(IDENTITY_TEST_KEY));
    }
    
    @Test
    public void testBuildWithHeader() {
        mockHeader(true);
        mockParameter(false);
        IdentityContext actual = identityContextBuilder.build(request);
        assertEquals(IDENTITY_TEST_VALUE, actual.getParameter(IDENTITY_TEST_KEY));
    }
    
    @Test
    public void testBuildWithParameter() {
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
