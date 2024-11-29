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

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.naming.remote.request.AbstractNamingRequest;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcProtocolAuthServiceTest {
    
    @Mock
    private AuthConfigs authConfigs;
    
    private ConfigPublishRequest configRequest;
    
    private AbstractNamingRequest namingRequest;
    
    private GrpcProtocolAuthService protocolAuthService;
    
    @BeforeEach
    void setUp() throws Exception {
        protocolAuthService = new GrpcProtocolAuthService(authConfigs);
        protocolAuthService.initialize();
        mockConfigRequest();
        mockNamingRequest();
    }
    
    private void mockConfigRequest() {
        configRequest = new ConfigPublishRequest();
        configRequest.setTenant("testCNs");
        configRequest.setGroup("testCG");
        configRequest.setDataId("testD");
    }
    
    private void mockNamingRequest() {
        namingRequest = new AbstractNamingRequest() {
        };
        namingRequest.setNamespace("testNNs");
        namingRequest.setGroupName("testNG");
        namingRequest.setServiceName("testS");
    }
    
    @Test
    @Secured(resource = "testResource")
    void testParseResourceWithSpecifiedResource() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithSpecifiedResource");
        Resource actual = protocolAuthService.parseResource(namingRequest, secured);
        assertEquals("testResource", actual.getName());
        assertEquals(SignType.SPECIFIED, actual.getType());
        assertNull(actual.getNamespaceId());
        assertNull(actual.getGroup());
        assertNotNull(actual.getProperties());
        assertTrue(actual.getProperties().isEmpty());
    }
    
    @Test
    @Secured(signType = "non-exist")
    void testParseResourceWithNonExistType() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithNonExistType");
        Resource actual = protocolAuthService.parseResource(namingRequest, secured);
        assertEquals(Resource.EMPTY_RESOURCE, actual);
    }
    
    @Test
    @Secured(signType = "non-exist", parser = MockResourceParser.class)
    void testParseResourceWithNonExistTypeException() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithNonExistTypeException");
        Resource actual = protocolAuthService.parseResource(namingRequest, secured);
        assertEquals(Resource.EMPTY_RESOURCE, actual);
    }
    
    @Test
    @Secured()
    void testParseResourceWithNamingType() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithNamingType");
        Resource actual = protocolAuthService.parseResource(namingRequest, secured);
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
        Resource actual = protocolAuthService.parseResource(configRequest, secured);
        assertEquals(SignType.CONFIG, actual.getType());
        assertEquals("testD", actual.getName());
        assertEquals("testCNs", actual.getNamespaceId());
        assertEquals("testCG", actual.getGroup());
        assertNotNull(actual.getProperties());
    }
    
    @Test
    void testParseIdentity() {
        IdentityContext actual = protocolAuthService.parseIdentity(namingRequest);
        assertNotNull(actual);
    }
    
    @Test
    void testValidateIdentityWithoutPlugin() throws AccessException {
        IdentityContext identityContext = new IdentityContext();
        assertTrue(protocolAuthService.validateIdentity(identityContext, Resource.EMPTY_RESOURCE));
    }
    
    @Test
    void testValidateIdentityWithPlugin() throws AccessException {
        Mockito.when(authConfigs.getNacosAuthSystemType()).thenReturn(MockAuthPluginService.TEST_PLUGIN);
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
        Mockito.when(authConfigs.getNacosAuthSystemType()).thenReturn(MockAuthPluginService.TEST_PLUGIN);
        assertFalse(protocolAuthService.validateAuthority(new IdentityContext(),
                new Permission(Resource.EMPTY_RESOURCE, "")));
    }
    
    @Test
    @Secured(signType = SignType.CONFIG)
    void testEnabledAuthWithPlugin() throws NoSuchMethodException {
        Mockito.when(authConfigs.getNacosAuthSystemType()).thenReturn(MockAuthPluginService.TEST_PLUGIN);
        Secured secured = getMethodSecure("testEnabledAuthWithPlugin");
        assertTrue(protocolAuthService.enableAuth(secured));
    }
    
    @Test
    @Secured(signType = SignType.CONFIG)
    void testEnabledAuthWithoutPlugin() throws NoSuchMethodException {
        Mockito.when(authConfigs.getNacosAuthSystemType()).thenReturn("non-exist-plugin");
        Secured secured = getMethodSecure("testEnabledAuthWithoutPlugin");
        assertFalse(protocolAuthService.enableAuth(secured));
    }
    
    @Test
    @Secured
    void testCheckServerIdentityWithoutIdentityConfig() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testCheckServerIdentityWithoutIdentityConfig");
        ServerIdentityResult result = protocolAuthService.checkServerIdentity(namingRequest, secured);
        assertEquals(ServerIdentityResult.ResultStatus.FAIL, result.getStatus());
        assertEquals("Invalid server identity key or value, Please make sure set `nacos.core.auth.server.identity.key`"
                        + " and `nacos.core.auth.server.identity.value`, or open `nacos.core.auth.enable.userAgentAuthWhite`",
                result.getMessage());
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        result = protocolAuthService.checkServerIdentity(namingRequest, secured);
        assertEquals(ServerIdentityResult.ResultStatus.FAIL, result.getStatus());
        assertEquals("Invalid server identity key or value, Please make sure set `nacos.core.auth.server.identity.key`"
                        + " and `nacos.core.auth.server.identity.value`, or open `nacos.core.auth.enable.userAgentAuthWhite`",
                result.getMessage());
    }
    
    @Test
    @Secured
    void testCheckServerIdentityNotMatched() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testCheckServerIdentityNotMatched");
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        when(authConfigs.getServerIdentityValue()).thenReturn("2");
        ServerIdentityResult result = protocolAuthService.checkServerIdentity(namingRequest, secured);
        assertEquals(ServerIdentityResult.ResultStatus.NOT_MATCHED, result.getStatus());
        namingRequest.putHeader("1", "3");
        result = protocolAuthService.checkServerIdentity(namingRequest, secured);
        assertEquals(ServerIdentityResult.ResultStatus.NOT_MATCHED, result.getStatus());
    }
    
    @Test
    @Secured
    void testCheckServerIdentityMatched() throws NoSuchMethodException {
        when(authConfigs.getServerIdentityKey()).thenReturn("1");
        when(authConfigs.getServerIdentityValue()).thenReturn("2");
        namingRequest.putHeader("1", "2");
        Secured secured = getMethodSecure("testCheckServerIdentityMatched");
        ServerIdentityResult result = protocolAuthService.checkServerIdentity(namingRequest, secured);
        assertEquals(ServerIdentityResult.ResultStatus.MATCHED, result.getStatus());
    }
    
    private Secured getMethodSecure(String methodName) throws NoSuchMethodException {
        Method method = GrpcProtocolAuthServiceTest.class.getDeclaredMethod(methodName);
        return method.getAnnotation(Secured.class);
    }
}
