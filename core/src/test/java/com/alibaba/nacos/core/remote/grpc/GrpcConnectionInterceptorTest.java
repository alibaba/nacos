/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.grpc;

import io.grpc.Attributes;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcConnectionInterceptorTest {
    
    @Mock
    private ServerCall<byte[], byte[]> serverCall;
    
    @Mock
    private Metadata headers;
    
    @Mock
    private ServerCallHandler<byte[], byte[]> next;
    
    @Test
    void testInterceptCallWithNonBiStreamService() {
        Attributes attrs = Attributes.newBuilder()
            .set(GrpcServerConstants.ATTR_TRANS_KEY_CONN_ID, "conn-1")
            .set(GrpcServerConstants.ATTR_TRANS_KEY_REMOTE_IP, "127.0.0.1")
            .set(GrpcServerConstants.ATTR_TRANS_KEY_REMOTE_PORT, 9848)
            .set(GrpcServerConstants.ATTR_TRANS_KEY_LOCAL_PORT, 8848)
            .build();
        when(serverCall.getAttributes()).thenReturn(attrs);
        MethodDescriptor<byte[], byte[]> methodDescriptor = mock(MethodDescriptor.class);
        when(methodDescriptor.getServiceName()).thenReturn("Request");
        when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        ServerCall.Listener<byte[]> listener = mock(ServerCall.Listener.class);
        when(next.startCall(any(), any())).thenReturn(listener);
        
        ServerInterceptor interceptor = new GrpcConnectionInterceptor();
        ServerCall.Listener<byte[]> result = interceptor.interceptCall(serverCall, headers, next);
        assertNotNull(result);
    }
}
