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

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.AuthPluginManager;
import com.alibaba.nacos.auth.AuthPluginService;
import com.alibaba.nacos.auth.api.IdentityContext;
import com.alibaba.nacos.auth.common.AuthConfigs;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GrpcIdentityContextBuilderTest {
    
    private static final String TEST_PLUGIN = "test";
    
    private static final String IDENTITY_TEST_KEY = "identity-test-key";
    
    private static final String IDENTITY_TEST_VALUE = "identity-test-value";
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private AuthPluginService authPluginService;
    
    @Mock
    private Request request;
    
    private GrpcIdentityContextBuilder identityContextBuilder;
    
    @Before
    public void setUp() throws Exception {
        identityContextBuilder = new GrpcIdentityContextBuilder(authConfigs);
        Field authServiceMapField = AuthPluginManager.class.getDeclaredField("authServiceMap");
        authServiceMapField.setAccessible(true);
        Map<String, AuthPluginService> authServiceMap = (Map<String, AuthPluginService>) authServiceMapField
                .get(AuthPluginManager.getInstance());
        authServiceMap.put(TEST_PLUGIN, authPluginService);
        when(authConfigs.getNacosAuthSystemType()).thenReturn(TEST_PLUGIN);
        when(authPluginService.identityNames()).thenReturn(Collections.singletonList(IDENTITY_TEST_KEY));
        Map<String, String> headers = new HashMap<>();
        headers.put(IDENTITY_TEST_KEY, IDENTITY_TEST_VALUE);
        when(request.getHeaders()).thenReturn(headers);
    }
    
    @Test
    public void testBuildWithoutPlugin() {
        when(authConfigs.getNacosAuthSystemType()).thenReturn("non-exist");
        IdentityContext actual = identityContextBuilder.build(request);
        assertNull(actual.getParameter(IDENTITY_TEST_KEY));
    }
    
    @Test
    public void testBuild() {
        IdentityContext actual = identityContextBuilder.build(request);
        assertEquals(IDENTITY_TEST_VALUE, actual.getParameter(IDENTITY_TEST_KEY));
    }
}
