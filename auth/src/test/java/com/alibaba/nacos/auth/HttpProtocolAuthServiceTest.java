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

package com.alibaba.nacos.auth;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.auth.mock.MockAuthPluginService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(MockitoJUnitRunner.class)
public class HttpProtocolAuthServiceTest {
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private HttpServletRequest request;
    
    private HttpProtocolAuthService httpProtocolAuthService;
    
    @Before
    public void setUp() throws Exception {
        httpProtocolAuthService = new HttpProtocolAuthService(authConfigs);
        httpProtocolAuthService.initialize();
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("testNNs");
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("testNG");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("testS");
        Mockito.when(request.getParameter(eq("tenant"))).thenReturn("testCNs");
        Mockito.when(request.getParameter(eq(Constants.GROUP))).thenReturn("testCG");
        Mockito.when(request.getParameter(eq(Constants.DATAID))).thenReturn("testD");
    }
    
    @Test
    @Secured(resource = "testResource")
    public void testParseResourceWithSpecifiedResource() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithSpecifiedResource");
        Resource actual = httpProtocolAuthService.parseResource(request, secured);
        assertEquals("testResource", actual.getName());
        assertEquals(SignType.SPECIFIED, actual.getType());
        assertNull(actual.getNamespaceId());
        assertNull(actual.getGroup());
        assertNull(actual.getProperties());
    }
    
    @Test
    @Secured(signType = "non-exist")
    public void testParseResourceWithNonExistType() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithNonExistType");
        Resource actual = httpProtocolAuthService.parseResource(request, secured);
        assertEquals(Resource.EMPTY_RESOURCE, actual);
    }
    
    @Test
    @Secured()
    public void testParseResourceWithNamingType() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithNamingType");
        Resource actual = httpProtocolAuthService.parseResource(request, secured);
        assertEquals(SignType.NAMING, actual.getType());
        assertEquals("testS", actual.getName());
        assertEquals("testNNs", actual.getNamespaceId());
        assertEquals("testNG", actual.getGroup());
        assertNotNull(actual.getProperties());
    }
    
    @Test
    @Secured(signType = SignType.CONFIG)
    public void testParseResourceWithConfigType() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithConfigType");
        Resource actual = httpProtocolAuthService.parseResource(request, secured);
        assertEquals(SignType.CONFIG, actual.getType());
        assertEquals("testD", actual.getName());
        assertEquals("testCNs", actual.getNamespaceId());
        assertEquals("testCG", actual.getGroup());
        assertNotNull(actual.getProperties());
    }
    
    @Test
    public void testParseIdentity() {
        IdentityContext actual = httpProtocolAuthService.parseIdentity(request);
        assertNotNull(actual);
    }
    
    @Test
    public void testValidateIdentityWithoutPlugin() throws AccessException {
        IdentityContext identityContext = new IdentityContext();
        assertTrue(httpProtocolAuthService.validateIdentity(identityContext, Resource.EMPTY_RESOURCE));
    }
    
    @Test
    public void testValidateIdentityWithPlugin() throws AccessException {
        Mockito.when(authConfigs.getNacosAuthSystemType()).thenReturn(MockAuthPluginService.TEST_PLUGIN);
        IdentityContext identityContext = new IdentityContext();
        assertFalse(httpProtocolAuthService.validateIdentity(identityContext, Resource.EMPTY_RESOURCE));
    }
    
    @Test
    public void testValidateAuthorityWithoutPlugin() throws AccessException {
        assertTrue(httpProtocolAuthService
                .validateAuthority(new IdentityContext(), new Permission(Resource.EMPTY_RESOURCE, "")));
    }
    
    @Test
    public void testValidateAuthorityWithPlugin() throws AccessException {
        Mockito.when(authConfigs.getNacosAuthSystemType()).thenReturn(MockAuthPluginService.TEST_PLUGIN);
        assertFalse(httpProtocolAuthService
                .validateAuthority(new IdentityContext(), new Permission(Resource.EMPTY_RESOURCE, "")));
    }
    
    private Secured getMethodSecure(String methodName) throws NoSuchMethodException {
        Method method = HttpProtocolAuthServiceTest.class.getMethod(methodName);
        return method.getAnnotation(Secured.class);
    }
}
