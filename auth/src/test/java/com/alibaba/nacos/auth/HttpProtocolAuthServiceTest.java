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
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.auth.mock.MockAuthPluginService;
import com.alibaba.nacos.auth.mock.MockResourceParser;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpProtocolAuthServiceTest {
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private HttpServletRequest request;
    
    private HttpProtocolAuthService protocolAuthService;
    
    @BeforeEach
    void setUp() throws Exception {
        protocolAuthService = new HttpProtocolAuthService(authConfigs);
        protocolAuthService.initialize();
        when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("testNNs");
        when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("testNG");
        when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("testS");
        when(request.getParameter(eq("tenant"))).thenReturn("testCNs");
        when(request.getParameter(eq(Constants.GROUP))).thenReturn("testCG");
        when(request.getParameter(eq(Constants.DATA_ID))).thenReturn("testD");
    }
    
    @Test
    @Secured(resource = "testResource", tags = {"testTag"})
    void testParseResourceWithSpecifiedResource() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithSpecifiedResource");
        Resource actual = protocolAuthService.parseResource(request, secured);
        assertEquals("testResource", actual.getName());
        assertEquals(SignType.SPECIFIED, actual.getType());
        assertNull(actual.getNamespaceId());
        assertNull(actual.getGroup());
        assertNotNull(actual.getProperties());
        assertEquals(1, actual.getProperties().size());
        assertEquals("testTag", actual.getProperties().get("testTag"));
    }
    
    @Test
    @Secured(signType = "non-exist")
    void testParseResourceWithNonExistType() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithNonExistType");
        Resource actual = protocolAuthService.parseResource(request, secured);
        assertEquals(Resource.EMPTY_RESOURCE, actual);
    }
    
    @Test
    @Secured(signType = "non-exist", parser = MockResourceParser.class)
    void testParseResourceWithNonExistTypeException() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithNonExistTypeException");
        Resource actual = protocolAuthService.parseResource(request, secured);
        assertEquals(Resource.EMPTY_RESOURCE, actual);
    }
    
    @Test
    @Secured()
    void testParseResourceWithNamingType() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithNamingType");
        Resource actual = protocolAuthService.parseResource(request, secured);
        assertEquals(SignType.NAMING, actual.getType());
        assertEquals("testS", actual.getName());
        assertEquals("testNNs", actual.getNamespaceId());
        assertEquals("testNG", actual.getGroup());
        assertNotNull(actual.getProperties());
    }
    
    @Test
    @Secured(signType = SignType.CONFIG)
    void testParseResourceWithConfigType() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithConfigType");
        Resource actual = protocolAuthService.parseResource(request, secured);
        assertEquals(SignType.CONFIG, actual.getType());
        assertEquals("testD", actual.getName());
        assertEquals("testNNs", actual.getNamespaceId());
        assertEquals("testCG", actual.getGroup());
        assertNotNull(actual.getProperties());
    }
    
    @Test
    void testParseIdentity() {
        IdentityContext actual = protocolAuthService.parseIdentity(request);
        assertNotNull(actual);
    }
    
    @Test
    void testValidateIdentityWithoutPlugin() throws AccessException {
        IdentityContext identityContext = new IdentityContext();
        assertTrue(protocolAuthService.validateIdentity(identityContext, Resource.EMPTY_RESOURCE));
    }
    
    @Test
    void testValidateIdentityWithPlugin() throws AccessException {
        when(authConfigs.getNacosAuthSystemType()).thenReturn(MockAuthPluginService.TEST_PLUGIN);
        IdentityContext identityContext = new IdentityContext();
        assertFalse(protocolAuthService.validateIdentity(identityContext, Resource.EMPTY_RESOURCE));
    }
    
    @Test
    void testValidateAuthorityWithoutPlugin() throws AccessException {
        assertTrue(protocolAuthService.validateAuthority(new IdentityContext(),
                new Permission(Resource.EMPTY_RESOURCE, "")));
    }
    
    @Test
    void testValidateAuthorityWithPlugin() throws AccessException {
        when(authConfigs.getNacosAuthSystemType()).thenReturn(MockAuthPluginService.TEST_PLUGIN);
        assertFalse(protocolAuthService.validateAuthority(new IdentityContext(),
                new Permission(Resource.EMPTY_RESOURCE, "")));
    }
    
    @Test
    @Secured(signType = SignType.CONFIG)
    void testEnabledAuthWithPlugin() throws NoSuchMethodException {
        when(authConfigs.getNacosAuthSystemType()).thenReturn(MockAuthPluginService.TEST_PLUGIN);
        Secured secured = getMethodSecure("testEnabledAuthWithPlugin");
        assertTrue(protocolAuthService.enableAuth(secured));
    }
    
    @Test
    @Secured(signType = SignType.CONFIG)
    void testEnabledAuthWithoutPlugin() throws NoSuchMethodException {
        when(authConfigs.getNacosAuthSystemType()).thenReturn("non-exist-plugin");
        Secured secured = getMethodSecure("testEnabledAuthWithoutPlugin");
        assertFalse(protocolAuthService.enableAuth(secured));
    }
    
    @Test
    void testCheckServerIdentityWithoutIdentityConfig() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testCheckServerIdentityWithoutIdentityConfig");
        ServerIdentityResult result = protocolAuthService.checkServerIdentity(request, secured);
        assertEquals(ServerIdentityResult.ResultStatus.FAIL, result.getStatus());
        assertEquals("Invalid server identity key or value, Please make sure set `nacos.core.auth.server.identity.key`"
                        + " and `nacos.core.auth.server.identity.value`, or open `nacos.core.auth.enable.userAgentAuthWhite`",
                result.getMessage());
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        result = protocolAuthService.checkServerIdentity(request, secured);
        assertEquals(ServerIdentityResult.ResultStatus.FAIL, result.getStatus());
        assertEquals("Invalid server identity key or value, Please make sure set `nacos.core.auth.server.identity.key`"
                        + " and `nacos.core.auth.server.identity.value`, or open `nacos.core.auth.enable.userAgentAuthWhite`",
                result.getMessage());
    }
    
    @Test
    void testCheckServerIdentityNotMatched() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testCheckServerIdentityNotMatched");
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        when(authConfigs.getServerIdentityValue()).thenReturn("2");
        ServerIdentityResult result = protocolAuthService.checkServerIdentity(request, secured);
        assertEquals(ServerIdentityResult.ResultStatus.NOT_MATCHED, result.getStatus());
        when(request.getHeader("1")).thenReturn("3");
        result = protocolAuthService.checkServerIdentity(request, secured);
        assertEquals(ServerIdentityResult.ResultStatus.NOT_MATCHED, result.getStatus());
    }
    
    @Test
    void testCheckServerIdentityMatched() throws NoSuchMethodException {
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        when(authConfigs.getServerIdentityValue()).thenReturn("2");
        when(request.getHeader("1")).thenReturn("2");
        Secured secured = getMethodSecure("testCheckServerIdentityMatched");
        ServerIdentityResult result = protocolAuthService.checkServerIdentity(request, secured);
        assertEquals(ServerIdentityResult.ResultStatus.MATCHED, result.getStatus());
    }
    
    private Secured getMethodSecure(String methodName) throws NoSuchMethodException {
        Method method = HttpProtocolAuthServiceTest.class.getDeclaredMethod(methodName);
        return method.getAnnotation(Secured.class);
    }
}
