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
        assertNull(actual.getProperties());
    }
    
    @Test
    @Secured(signType = "non-exist")
    void testParseResourceWithNonExistType() throws NoSuchMethodException {
        Secured secured = getMethodSecure("testParseResourceWithNonExistType");
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
    
    private Secured getMethodSecure(String methodName) throws NoSuchMethodException {
        Method method = GrpcProtocolAuthServiceTest.class.getDeclaredMethod(methodName);
        return method.getAnnotation(Secured.class);
    }
}
