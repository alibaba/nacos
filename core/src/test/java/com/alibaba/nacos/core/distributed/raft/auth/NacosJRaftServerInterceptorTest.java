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

package com.alibaba.nacos.core.distributed.raft.auth;

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityChecker;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityResult;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosJRaftServerInterceptorTest {
    
    @Mock
    private JRaftAuthUpgradeCoordinator upgradeCoordinator;
    
    @Mock
    private NacosAuthConfig authConfig;
    
    @Mock
    private ServerIdentityChecker identityChecker;
    
    @Mock
    private ServerCall<Object, Object> serverCall;
    
    @Mock
    private ServerCallHandler<Object, Object> next;
    
    @Mock
    private ServerCall.Listener<Object> listener;
    
    private NacosJRaftServerInterceptor interceptor;
    
    @BeforeEach
    void setUp() {
        when(authConfig.getServerIdentityKey()).thenReturn("identity-key");
        when(authConfig.getServerIdentityValue()).thenReturn("identity-value");
        interceptor = new NacosJRaftServerInterceptor(upgradeCoordinator, authConfig,
            identityChecker);
    }
    
    @Test
    void testValidCredentialContinuesRequest() {
        Metadata headers = validHeaders();
        when(identityChecker.check(any(), any())).thenReturn(ServerIdentityResult.success());
        when(next.startCall(serverCall, headers)).thenReturn(listener);
        
        ServerCall.Listener<Object> result = interceptor.interceptCall(serverCall, headers, next);
        
        assertSame(listener, result);
        verify(upgradeCoordinator, never()).allowInvalidCredential();
    }
    
    @Test
    void testInvalidCredentialContinuesDuringCompatibilityWindow() {
        Metadata headers = new Metadata();
        when(upgradeCoordinator.allowInvalidCredential()).thenReturn(true);
        when(next.startCall(serverCall, headers)).thenReturn(listener);
        
        ServerCall.Listener<Object> result = interceptor.interceptCall(serverCall, headers, next);
        
        assertSame(listener, result);
        verify(next).startCall(serverCall, headers);
    }
    
    @Test
    void testInvalidCredentialRejectedAfterEnforcement() {
        Metadata headers = new Metadata();
        when(upgradeCoordinator.allowInvalidCredential()).thenReturn(false);
        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        
        interceptor.interceptCall(serverCall, headers, next);
        
        verify(serverCall).close(statusCaptor.capture(), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
        org.junit.jupiter.api.Assertions.assertEquals(Status.Code.UNAUTHENTICATED,
            statusCaptor.getValue().getCode());
    }
    
    @Test
    void testMismatchedIdentityKeyIsRejected() {
        Metadata headers = validHeaders();
        headers.discardAll(JRaftAuthMetadata.IDENTITY_KEY);
        headers.put(JRaftAuthMetadata.IDENTITY_KEY, "different-key");
        when(upgradeCoordinator.allowInvalidCredential()).thenReturn(false);
        
        interceptor.interceptCall(serverCall, headers, next);
        
        verify(identityChecker, never()).check(any(), any());
        verify(serverCall).close(any(Status.class), any(Metadata.class));
    }
    
    private Metadata validHeaders() {
        Metadata result = new Metadata();
        result.put(JRaftAuthMetadata.IDENTITY_KEY, "identity-key");
        result.put(JRaftAuthMetadata.IDENTITY_VALUE, "identity-value");
        return result;
    }
}
