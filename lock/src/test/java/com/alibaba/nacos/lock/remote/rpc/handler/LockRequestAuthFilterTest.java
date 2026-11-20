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

package com.alibaba.nacos.lock.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.api.lock.remote.request.LockOperationRequest;
import com.alibaba.nacos.api.lock.remote.response.LockOperationResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.auth.GrpcProtocolAuthService;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.core.auth.InnerApiAuthEnabled;
import com.alibaba.nacos.core.auth.RemoteRequestAuthFilter;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.remote.RequestFilters;
import com.alibaba.nacos.lock.service.LockOperationService;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end auth tests for the lock gRPC handler.
 *
 * <p>Verifies that {@link RemoteRequestAuthFilter} enforces the declared
 * {@code @Secured(action = WRITE, signType = LOCK)} contract on
 * {@link LockRequestHandler}: the lock resource is parsed from the request lock key, denied
 * identity or authority never reaches the business logic, and the denial response keeps the
 * lock response type.
 */
@ExtendWith(MockitoExtension.class)
class LockRequestAuthFilterTest {
    
    private RemoteRequestAuthFilter authFilter;
    
    @Mock
    private NacosAuthConfig authConfig;
    
    @Mock
    private InnerApiAuthEnabled innerApiAuthEnabled;
    
    @Mock
    private LockOperationService lockOperationService;
    
    private LockOperationRequest lockRequest;
    
    private RequestMeta requestMeta;
    
    private ConfigurableEnvironment originalEnvironment;
    
    @BeforeEach
    void setUp() {
        originalEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        environment.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, "nacos");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "test-key");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "test-value");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        authFilter = new RemoteRequestAuthFilter(innerApiAuthEnabled);
        GrpcProtocolAuthService protocolAuthService = new GrpcProtocolAuthService(authConfig);
        protocolAuthService.initialize();
        ReflectionTestUtils.setField(authFilter, "protocolAuthService", protocolAuthService);
        ReflectionTestUtils.setField(authFilter, "authConfig", authConfig);
        lockRequest = new LockOperationRequest();
        lockRequest.setLockInstance(
            new LockInstance("testKey", 30000L, LockConstants.REENTRANT_LOCK_TYPE));
        requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        requestMeta.setConnectionId("test-conn-id");
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
        EnvUtil.setEnvironment(originalEnvironment);
    }
    
    @Test
    void testFilterSkipsLockRequestWhenAuthDisabled() throws NacosException {
        when(authConfig.isAuthEnabled()).thenReturn(false);
        Response actual = authFilter.filter(lockRequest, requestMeta, LockRequestHandler.class);
        assertNull(actual);
        assertNull(RequestContextHolder.getContext().getAuthContext().getResource(),
            "no lock resource should be parsed when auth is disabled");
    }
    
    @Test
    void testFilterParsesLockResourceAndBlocksInvalidIdentity() throws NacosException {
        GrpcProtocolAuthService spyService = stubAuthEnabledService();
        when(spyService.validateIdentity(any(IdentityContext.class), any(Resource.class)))
            .thenReturn(AuthResult.failureResult(403, "invalid identity"));
        
        Response actual = authFilter.filter(lockRequest, requestMeta, LockRequestHandler.class);
        
        assertNotNull(actual);
        assertTrue(actual instanceof LockOperationResponse,
            "denial response must keep the lock response type");
        assertEquals(ResponseCode.FAIL.getCode(), actual.getResultCode());
        assertEquals(NacosException.NO_RIGHT, actual.getErrorCode());
        
        ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(spyService).validateIdentity(any(IdentityContext.class), resourceCaptor.capture());
        assertLockResource(resourceCaptor.getValue());
    }
    
    @Test
    void testFilterBlocksInsufficientAuthorityWithWritePermission() throws NacosException {
        GrpcProtocolAuthService spyService = stubAuthEnabledService();
        when(spyService.validateIdentity(any(IdentityContext.class), any(Resource.class)))
            .thenReturn(AuthResult.successResult());
        when(spyService.validateAuthority(any(IdentityContext.class), any(Permission.class)))
            .thenReturn(AuthResult.failureResult(403, "no permission"));
        
        Response actual = authFilter.filter(lockRequest, requestMeta, LockRequestHandler.class);
        
        assertNotNull(actual);
        assertTrue(actual instanceof LockOperationResponse);
        assertEquals(NacosException.NO_RIGHT, actual.getErrorCode());
        
        ArgumentCaptor<Permission> permissionCaptor = ArgumentCaptor.forClass(Permission.class);
        verify(spyService).validateAuthority(any(IdentityContext.class),
            permissionCaptor.capture());
        assertEquals("w", permissionCaptor.getValue().getAction(),
            "lock operations must be authorized as write actions");
        assertLockResource(permissionCaptor.getValue().getResource());
    }
    
    @Test
    void testFilterPassesAuthorizedLockRequest() throws NacosException {
        GrpcProtocolAuthService spyService = stubAuthEnabledService();
        when(spyService.validateIdentity(any(IdentityContext.class), any(Resource.class)))
            .thenReturn(AuthResult.successResult());
        when(spyService.validateAuthority(any(IdentityContext.class), any(Permission.class)))
            .thenReturn(AuthResult.successResult());
        
        Response actual = authFilter.filter(lockRequest, requestMeta, LockRequestHandler.class);
        assertNull(actual);
    }
    
    @Test
    void testHandleRequestRejectsUnauthorizedRequestBeforeBusinessLogic() throws NacosException {
        LockRequestHandler handler = new LockRequestHandler(lockOperationService);
        GrpcProtocolAuthService spyService = stubAuthEnabledService();
        when(spyService.validateIdentity(any(IdentityContext.class), any(Resource.class)))
            .thenReturn(AuthResult.failureResult(403, "denied"));
        registerAuthFilterOnHandler(handler);
        
        Response actual = handler.handleRequest(lockRequest, requestMeta);
        
        assertNotNull(actual);
        assertFalse(actual.isSuccess());
        assertEquals(NacosException.NO_RIGHT, actual.getErrorCode());
        verify(lockOperationService, never()).lock(any(LockInstance.class), eq("test-conn-id"));
    }
    
    @Test
    void testHandleRequestInvokesBusinessLogicAfterAuthorizedFilter() throws NacosException {
        LockRequestHandler handler = new LockRequestHandler(lockOperationService);
        GrpcProtocolAuthService spyService = stubAuthEnabledService();
        when(spyService.validateIdentity(any(IdentityContext.class), any(Resource.class)))
            .thenReturn(AuthResult.successResult());
        when(spyService.validateAuthority(any(IdentityContext.class), any(Permission.class)))
            .thenReturn(AuthResult.successResult());
        registerAuthFilterOnHandler(handler);
        lockRequest.setLockOperationEnum(
            com.alibaba.nacos.api.lock.remote.LockOperationEnum.ACQUIRE);
        when(lockOperationService.lock(any(LockInstance.class), eq("test-conn-id")))
            .thenReturn(LockResult.success(1));
        
        Response actual = handler.handleRequest(lockRequest, requestMeta);
        
        assertNotNull(actual);
        assertTrue(actual.isSuccess());
        assertTrue(((LockOperationResponse) actual).getLockResult().isSuccess());
        verify(lockOperationService).lock(any(LockInstance.class), eq("test-conn-id"));
    }
    
    private GrpcProtocolAuthService stubAuthEnabledService() {
        when(authConfig.isAuthEnabled()).thenReturn(true);
        GrpcProtocolAuthService protocolAuthService = new GrpcProtocolAuthService(authConfig);
        protocolAuthService.initialize();
        GrpcProtocolAuthService spyService = spy(protocolAuthService);
        ReflectionTestUtils.setField(authFilter, "protocolAuthService", spyService);
        when(spyService.enableAuth(any(Secured.class))).thenReturn(true);
        doReturn(new IdentityContext()).when(spyService).parseIdentity(eq(lockRequest));
        return spyService;
    }
    
    private void registerAuthFilterOnHandler(LockRequestHandler handler) {
        RequestFilters requestFilters = new RequestFilters();
        requestFilters.registerFilter(authFilter);
        ReflectionTestUtils.setField(handler, "requestFilters", requestFilters);
    }
    
    private void assertLockResource(Resource resource) {
        assertEquals(SignType.LOCK, resource.getType());
        assertEquals("testKey", resource.getName());
        assertEquals(StringUtils.EMPTY, resource.getNamespaceId());
        assertEquals(StringUtils.EMPTY, resource.getGroup());
    }
}
