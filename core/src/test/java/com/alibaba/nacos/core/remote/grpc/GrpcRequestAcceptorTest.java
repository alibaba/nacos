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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.common.remote.PayloadRegistry;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link GrpcRequestAcceptor} unit test.
 *
 * @author chenglu
 * @date 2021-07-01 10:49
 */
@ExtendWith({MockitoExtension.class, GrpcCleanupExtension.class})
public class GrpcRequestAcceptorTest {
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private RequestHandlerRegistry requestHandlerRegistry;
    
    @InjectMocks
    private GrpcRequestAcceptor acceptor;
    
    private RequestGrpc.RequestStub streamStub;
    
    private String connectId = UUID.randomUUID().toString();
    
    private String requestId = UUID.randomUUID().toString();
    
    private MockRequestHandler mockHandler;
    
    @BeforeEach
    void setUp(Resources resources) throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        String remoteIp = "127.0.0.1";
        resources.register(
                InProcessServerBuilder.forName(serverName).directExecutor().addService(acceptor).intercept(new ServerInterceptor() {
                    @Override
                    public <R, S> ServerCall.Listener<R> interceptCall(ServerCall<R, S> serverCall, Metadata metadata,
                            ServerCallHandler<R, S> serverCallHandler) {
                        Context ctx = Context.current().withValue(GrpcServerConstants.CONTEXT_KEY_CONN_ID, UUID.randomUUID().toString())
                                .withValue(GrpcServerConstants.CONTEXT_KEY_CONN_LOCAL_PORT, 1234)
                                .withValue(GrpcServerConstants.CONTEXT_KEY_CONN_REMOTE_PORT, 8948)
                                .withValue(GrpcServerConstants.CONTEXT_KEY_CONN_REMOTE_IP, remoteIp);
                        return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
                    }
                }).build().start(), Duration.ofSeconds(20L));
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        resources.register(channel, Duration.ofSeconds(20L));
        streamStub = RequestGrpc.newStub(channel);
        mockHandler = new MockRequestHandler();
        PayloadRegistry.init();
    }
    
    @Test
    void testApplicationUnStarted() {
        RequestMeta metadata = new RequestMeta();
        metadata.setClientIp("127.0.0.1");
        metadata.setConnectionId(connectId);
        ServerCheckRequest serverCheckRequest = new ServerCheckRequest();
        serverCheckRequest.setRequestId(requestId);
        Payload request = GrpcUtils.convert(serverCheckRequest, metadata);
        
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                System.out.println("Receive data from server: " + payload);
                Object res = GrpcUtils.parse(payload);
                assertTrue(res instanceof ErrorResponse);
                ErrorResponse errorResponse = (ErrorResponse) res;
                assertEquals(NacosException.INVALID_SERVER_STATUS, errorResponse.getErrorCode());
            }
            
            @Override
            public void onError(Throwable throwable) {
                fail(throwable.getMessage());
            }
            
            @Override
            public void onCompleted() {
                System.out.println("complete");
            }
        };
        
        streamStub.request(request, streamObserver);
    }
    
    @Test
    void testServerCheckRequest() {
        ApplicationUtils.setStarted(true);
        RequestMeta metadata = new RequestMeta();
        metadata.setClientIp("127.0.0.1");
        metadata.setConnectionId(connectId);
        ServerCheckRequest serverCheckRequest = new ServerCheckRequest();
        serverCheckRequest.setRequestId(requestId);
        Payload request = GrpcUtils.convert(serverCheckRequest, metadata);
        
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                System.out.println("Receive data from server: " + payload);
                Object res = GrpcUtils.parse(payload);
                assertTrue(res instanceof ServerCheckResponse);
            }
            
            @Override
            public void onError(Throwable throwable) {
                fail(throwable.getMessage());
            }
            
            @Override
            public void onCompleted() {
                System.out.println("complete");
            }
        };
        
        streamStub.request(request, streamObserver);
        ApplicationUtils.setStarted(false);
    }
    
    @Test
    void testNoRequestHandler() {
        ApplicationUtils.setStarted(true);
        RequestMeta metadata = new RequestMeta();
        metadata.setClientIp("127.0.0.1");
        metadata.setConnectionId(connectId);
        InstanceRequest instanceRequest = new InstanceRequest();
        instanceRequest.setRequestId(requestId);
        Payload request = GrpcUtils.convert(instanceRequest, metadata);
        
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                System.out.println("Receive data from server: " + payload);
                Object res = GrpcUtils.parse(payload);
                assertTrue(res instanceof ErrorResponse);
                
                ErrorResponse errorResponse = (ErrorResponse) res;
                assertEquals(NacosException.NO_HANDLER, errorResponse.getErrorCode());
            }
            
            @Override
            public void onError(Throwable throwable) {
                fail(throwable.getMessage());
            }
            
            @Override
            public void onCompleted() {
                System.out.println("complete");
            }
        };
        
        streamStub.request(request, streamObserver);
        ApplicationUtils.setStarted(false);
    }
    
    @Test
    void testConnectionNotRegister() {
        ApplicationUtils.setStarted(true);
        Mockito.when(requestHandlerRegistry.getByRequestType(Mockito.anyString())).thenReturn(mockHandler);
        Mockito.when(connectionManager.checkValid(Mockito.any())).thenReturn(false);
        
        RequestMeta metadata = new RequestMeta();
        metadata.setClientIp("127.0.0.1");
        metadata.setConnectionId(connectId);
        InstanceRequest instanceRequest = new InstanceRequest();
        instanceRequest.setRequestId(requestId);
        Payload request = GrpcUtils.convert(instanceRequest, metadata);
        
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                System.out.println("Receive data from server: " + payload);
                Object res = GrpcUtils.parse(payload);
                assertTrue(res instanceof ErrorResponse);
                
                ErrorResponse errorResponse = (ErrorResponse) res;
                assertEquals(NacosException.UN_REGISTER, errorResponse.getErrorCode());
            }
            
            @Override
            public void onError(Throwable throwable) {
                fail(throwable.getMessage());
            }
            
            @Override
            public void onCompleted() {
                System.out.println("complete");
            }
        };
        
        streamStub.request(request, streamObserver);
        ApplicationUtils.setStarted(false);
    }
    
    @Test
    void testRequestContentError() {
        ApplicationUtils.setStarted(true);
        Mockito.when(requestHandlerRegistry.getByRequestType(Mockito.anyString())).thenReturn(mockHandler);
        Mockito.when(connectionManager.checkValid(Mockito.any())).thenReturn(true);
        
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                System.out.println("Receive data from server: " + payload);
                Object res = GrpcUtils.parse(payload);
                assertTrue(res instanceof ErrorResponse);
                
                ErrorResponse errorResponse = (ErrorResponse) res;
                assertEquals(NacosException.BAD_GATEWAY, errorResponse.getErrorCode());
            }
            
            @Override
            public void onError(Throwable throwable) {
                fail(throwable.getMessage());
            }
            
            @Override
            public void onCompleted() {
                System.out.println("complete");
            }
        };
        
        streamStub.request(null, streamObserver);
        ApplicationUtils.setStarted(false);
    }
    
    @Test
    void testHandleRequestSuccess() {
        ApplicationUtils.setStarted(true);
        Mockito.when(requestHandlerRegistry.getByRequestType(Mockito.anyString())).thenReturn(mockHandler);
        Mockito.when(connectionManager.checkValid(Mockito.any())).thenReturn(true);
        String ip = "1.1.1.1";
        ConnectionMeta connectionMeta = new ConnectionMeta(connectId, ip, ip, 8888, 9848, "GRPC", "", "", new HashMap<>());
        Connection connection = new GrpcConnection(connectionMeta, null, null);
        Mockito.when(connectionManager.getConnection(Mockito.any())).thenReturn(connection);
        
        RequestMeta metadata = new RequestMeta();
        metadata.setClientIp("127.0.0.1");
        metadata.setConnectionId(connectId);
        HealthCheckRequest mockRequest = new HealthCheckRequest();
        Payload payload = GrpcUtils.convert(mockRequest, metadata);
        
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                System.out.println("Receive data from server: " + payload);
                Object res = GrpcUtils.parse(payload);
                assertTrue(res instanceof HealthCheckResponse);
            }
            
            @Override
            public void onError(Throwable throwable) {
                fail(throwable.getMessage());
            }
            
            @Override
            public void onCompleted() {
                System.out.println("complete");
            }
        };
        
        streamStub.request(payload, streamObserver);
        ApplicationUtils.setStarted(false);
    }
    
    @Test
    void testHandleRequestError() {
        ApplicationUtils.setStarted(true);
        Mockito.when(requestHandlerRegistry.getByRequestType(Mockito.anyString())).thenReturn(mockHandler);
        Mockito.when(connectionManager.checkValid(Mockito.any())).thenReturn(true);
        
        RequestMeta metadata = new RequestMeta();
        metadata.setClientIp("127.0.0.1");
        metadata.setConnectionId(connectId);
        InstanceRequest instanceRequest = new InstanceRequest();
        Payload payload = GrpcUtils.convert(instanceRequest, metadata);
        
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                System.out.println("Receive data from server: " + payload);
                Object res = GrpcUtils.parse(payload);
                assertTrue(res instanceof ErrorResponse);
                
                ErrorResponse errorResponse = (ErrorResponse) res;
                assertEquals(NacosException.SERVER_ERROR, errorResponse.getErrorCode());
            }
            
            @Override
            public void onError(Throwable throwable) {
                fail(throwable.getMessage());
            }
            
            @Override
            public void onCompleted() {
                System.out.println("complete");
            }
        };
        
        streamStub.request(payload, streamObserver);
        ApplicationUtils.setStarted(false);
    }
    
    /**
     * add this Handler just for test.
     */
    class MockRequestHandler extends RequestHandler<HealthCheckRequest, HealthCheckResponse> {
        
        @Override
        public Response handleRequest(HealthCheckRequest request, RequestMeta meta) throws NacosException {
            return handle(request, meta);
        }
        
        @Override
        public HealthCheckResponse handle(HealthCheckRequest request, RequestMeta meta) throws NacosException {
            System.out.println("MockHandler get request: " + request + " meta: " + meta);
            return new HealthCheckResponse();
        }
    }
}
