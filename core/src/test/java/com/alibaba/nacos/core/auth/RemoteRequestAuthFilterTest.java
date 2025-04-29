/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.auth.GrpcProtocolAuthService;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.remote.HealthCheckRequestHandler;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * {@link RemoteRequestAuthFilter} unit test.
 *
 * @author chenglu
 * @date 2021-07-06 16:14
 */
@ExtendWith(MockitoExtension.class)
class RemoteRequestAuthFilterTest {
    
    private RemoteRequestAuthFilter authFilter;
    
    @Mock
    private NacosAuthConfig authConfig;
    
    @Mock
    private InnerApiAuthEnabled innerApiAuthEnabled;
    
    @Mock
    Request request;
    
    @Mock
    RequestMeta requestMeta;
    
    MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        authFilter = new RemoteRequestAuthFilter(innerApiAuthEnabled);
        GrpcProtocolAuthService protocolAuthService = new GrpcProtocolAuthService(authConfig);
        protocolAuthService.initialize();
        ReflectionTestUtils.setField(authFilter, "protocolAuthService", protocolAuthService);
        ReflectionTestUtils.setField(authFilter, "authConfig", authConfig);
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testFilterWithoutSecured() throws NacosException {
        Response actual = authFilter.filter(request, requestMeta, HealthCheckRequestHandler.class);
        assertNull(actual);
    }
    
    @Test
    void testFilterDisabledAuth() throws NacosException {
        when(authConfig.isAuthEnabled()).thenReturn(false);
        Response actual = authFilter.filter(request, requestMeta, MockRequestHandler.class);
        assertNull(actual);
    }
    
    @Test
    void testFilterWithoutServerIdentity() throws NacosException {
        when(innerApiAuthEnabled.isEnabled()).thenReturn(true);
        Response actual = authFilter.filter(request, requestMeta, MockInnerRequestHandler.class);
        assertNotNull(actual);
        assertEquals(ResponseCode.FAIL.getCode(), actual.getResultCode());
        assertEquals(403, actual.getErrorCode());
        assertEquals("Invalid server identity key or value, Please make sure set `nacos.core.auth.server.identity.key`"
                        + " and `nacos.core.auth.server.identity.value`, or open `nacos.core.auth.enable.userAgentAuthWhite`",
                actual.getMessage());
    }
    
    @Test
    void testFilterDisabledAuthWithInnerApi() throws NacosException {
        when(innerApiAuthEnabled.isEnabled()).thenReturn(true);
        when(authConfig.getServerIdentityKey()).thenReturn("1");
        when(authConfig.getServerIdentityValue()).thenReturn("2");
        Response actual = authFilter.filter(request, requestMeta, MockInnerRequestHandler.class);
        assertNull(actual);
    }
    
    @Test
    void testFilterWithoutServerIdentityForInnerApiAuthDisabled() throws NacosException {
        when(innerApiAuthEnabled.isEnabled()).thenReturn(false);
        Response actual = authFilter.filter(request, requestMeta, MockInnerRequestHandler.class);
        assertNull(actual);
    }
    
    @Test
    void testFilterWithServerIdentity() throws NacosException {
        when(innerApiAuthEnabled.isEnabled()).thenReturn(true);
        when(authConfig.getServerIdentityKey()).thenReturn("1");
        when(authConfig.getServerIdentityValue()).thenReturn("2");
        when(request.getHeader("1")).thenReturn("2");
        Response actual = authFilter.filter(request, requestMeta, MockInnerRequestHandler.class);
        assertNull(actual);
    }
    
    @Test
    void testFilterWithNoNeedAuthSecured() throws NacosException {
        when(authConfig.isAuthEnabled()).thenReturn(true);
        GrpcProtocolAuthService protocolAuthService = injectMockPlugins();
        when(protocolAuthService.enableAuth(any(Secured.class))).thenReturn(false);
        Response actual = authFilter.filter(request, requestMeta, MockRequestHandler.class);
        assertNull(actual);
    }
    
    @Test
    void testFilterWithNeedAuthSecuredSuccess() throws NacosException {
        when(authConfig.isAuthEnabled()).thenReturn(true);
        GrpcProtocolAuthService protocolAuthService = injectMockPlugins();
        when(protocolAuthService.enableAuth(any(Secured.class))).thenReturn(true);
        doReturn(new IdentityContext()).when(protocolAuthService).parseIdentity(eq(request));
        doReturn(Resource.EMPTY_RESOURCE).when(protocolAuthService).parseResource(eq(request), any(Secured.class));
        when(protocolAuthService.validateIdentity(any(IdentityContext.class), any(Resource.class))).thenReturn(
                AuthResult.successResult());
        when(protocolAuthService.validateAuthority(any(IdentityContext.class), any(Permission.class))).thenReturn(
                AuthResult.successResult());
        Response actual = authFilter.filter(request, requestMeta, MockRequestHandler.class);
        assertNull(actual);
    }
    
    @Test
    @Secured
    void testFilterWithNeedAuthSecuredIdentityFailure() throws NacosException {
        when(authConfig.isAuthEnabled()).thenReturn(true);
        GrpcProtocolAuthService protocolAuthService = injectMockPlugins();
        when(protocolAuthService.enableAuth(any(Secured.class))).thenReturn(true);
        doReturn(new IdentityContext()).when(protocolAuthService).parseIdentity(eq(request));
        doReturn(Resource.EMPTY_RESOURCE).when(protocolAuthService).parseResource(eq(request), any(Secured.class));
        when(protocolAuthService.validateIdentity(any(IdentityContext.class), any(Resource.class))).thenReturn(
                AuthResult.failureResult(403, "test"));
        Response actual = authFilter.filter(request, requestMeta, MockRequestHandler.class);
        assertNotNull(actual);
        assertEquals(ResponseCode.FAIL.getCode(), actual.getResultCode());
        assertEquals(403, actual.getErrorCode());
    }
    
    @Test
    @Secured
    void testDoFilterWithNeedAuthSecuredAuthorityFailure() throws NacosException {
        when(authConfig.isAuthEnabled()).thenReturn(true);
        GrpcProtocolAuthService protocolAuthService = injectMockPlugins();
        when(protocolAuthService.enableAuth(any(Secured.class))).thenReturn(true);
        doReturn(new IdentityContext()).when(protocolAuthService).parseIdentity(eq(request));
        doReturn(Resource.EMPTY_RESOURCE).when(protocolAuthService).parseResource(eq(request), any(Secured.class));
        when(protocolAuthService.validateIdentity(any(IdentityContext.class), any(Resource.class))).thenReturn(
                AuthResult.successResult());
        when(protocolAuthService.validateAuthority(any(IdentityContext.class), any(Permission.class))).thenReturn(
                AuthResult.failureResult(403, "test"));
        Response actual = authFilter.filter(request, requestMeta, MockRequestHandler.class);
        assertNotNull(actual);
        assertEquals(ResponseCode.FAIL.getCode(), actual.getResultCode());
        assertEquals(403, actual.getErrorCode());
    }
    
    private GrpcProtocolAuthService injectMockPlugins() {
        GrpcProtocolAuthService protocolAuthService = new GrpcProtocolAuthService(authConfig);
        protocolAuthService.initialize();
        GrpcProtocolAuthService spyProtocolAuthService = spy(protocolAuthService);
        ReflectionTestUtils.setField(authFilter, "protocolAuthService", spyProtocolAuthService);
        return spyProtocolAuthService;
    }
    
    static class MockRequestHandler extends RequestHandler<Request, HealthCheckResponse> {
        
        @Secured(resource = "xxx")
        @Override
        public HealthCheckResponse handle(Request request, RequestMeta meta) throws NacosException {
            return new HealthCheckResponse();
        }
    }
    
    static class MockInnerRequestHandler extends RequestHandler<Request, HealthCheckResponse> {
        
        @Secured(resource = "xxx", apiType = ApiType.INNER_API)
        @Override
        public HealthCheckResponse handle(Request request, RequestMeta meta) throws NacosException {
            return new HealthCheckResponse();
        }
    }
}
