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
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_ID;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_LOCAL_PORT;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_REMOTE_IP;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_REMOTE_PORT;

/**
 * {@link GrpcRequestAcceptor} unit test.
 *
 * @author chenglu
 * @date 2021-07-01 10:49
 */
@RunWith(MockitoJUnitRunner.class)
public class GrpcRequestAcceptorTest {
    
    @Rule
    public GrpcCleanupRule grpcCleanupRule = new GrpcCleanupRule();
    
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
    
    @Before
    public void setUp() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        String remoteIp = "127.0.0.1";
        grpcCleanupRule.register(InProcessServerBuilder.forName(serverName).directExecutor().addService(acceptor)
                .intercept(new ServerInterceptor() {
                    @Override
                    public <R, S> ServerCall.Listener<R> interceptCall(ServerCall<R, S> serverCall, Metadata metadata,
                            ServerCallHandler<R, S> serverCallHandler) {
                        Context ctx = Context.current().withValue(CONTEXT_KEY_CONN_ID, UUID.randomUUID().toString())
                                .withValue(CONTEXT_KEY_CONN_LOCAL_PORT, 1234)
                                .withValue(CONTEXT_KEY_CONN_REMOTE_PORT, 8948)
                                .withValue(CONTEXT_KEY_CONN_REMOTE_IP, remoteIp);
                        return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
                    }
                }).build().start());
        streamStub = RequestGrpc.newStub(
                grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
        mockHandler = new MockRequestHandler();
        PayloadRegistry.init();
    }
    
    @Test
    public void testApplicationUnStarted() {
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
                Assert.assertTrue(res instanceof ErrorResponse);
                ErrorResponse errorResponse = (ErrorResponse) res;
                Assert.assertEquals(errorResponse.getErrorCode(), NacosException.INVALID_SERVER_STATUS);
            }
            
            @Override
            public void onError(Throwable throwable) {
                Assert.fail(throwable.getMessage());
            }
            
            @Override
            public void onCompleted() {
                System.out.println("complete");
            }
        };
        
        streamStub.request(request, streamObserver);
    }
    
    @Test
    public void testServerCheckRequest() {
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
                Assert.assertTrue(res instanceof ServerCheckResponse);
            }
            
            @Override
            public void onError(Throwable throwable) {
                Assert.fail(throwable.getMessage());
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
    public void testNoRequestHandler() {
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
                Assert.assertTrue(res instanceof ErrorResponse);
                
                ErrorResponse errorResponse = (ErrorResponse) res;
                Assert.assertEquals(errorResponse.getErrorCode(), NacosException.NO_HANDLER);
            }
            
            @Override
            public void onError(Throwable throwable) {
                Assert.fail(throwable.getMessage());
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
    public void testConnectionNotRegister() {
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
                Assert.assertTrue(res instanceof ErrorResponse);
                
                ErrorResponse errorResponse = (ErrorResponse) res;
                Assert.assertEquals(errorResponse.getErrorCode(), NacosException.UN_REGISTER);
            }
            
            @Override
            public void onError(Throwable throwable) {
                Assert.fail(throwable.getMessage());
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
    public void testRequestContentError() {
        ApplicationUtils.setStarted(true);
        Mockito.when(requestHandlerRegistry.getByRequestType(Mockito.anyString())).thenReturn(mockHandler);
        Mockito.when(connectionManager.checkValid(Mockito.any())).thenReturn(true);
        
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                System.out.println("Receive data from server: " + payload);
                Object res = GrpcUtils.parse(payload);
                Assert.assertTrue(res instanceof ErrorResponse);
                
                ErrorResponse errorResponse = (ErrorResponse) res;
                Assert.assertEquals(errorResponse.getErrorCode(), NacosException.BAD_GATEWAY);
            }
            
            @Override
            public void onError(Throwable throwable) {
                Assert.fail(throwable.getMessage());
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
    public void testHandleRequestSuccess() {
        ApplicationUtils.setStarted(true);
        Mockito.when(requestHandlerRegistry.getByRequestType(Mockito.anyString())).thenReturn(mockHandler);
        Mockito.when(connectionManager.checkValid(Mockito.any())).thenReturn(true);
        String ip = "1.1.1.1";
        ConnectionMeta connectionMeta = new ConnectionMeta(connectId, ip, ip, 8888, 9848, "GRPC", "", "",
                new HashMap<>());
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
                Assert.assertTrue(res instanceof HealthCheckResponse);
            }
            
            @Override
            public void onError(Throwable throwable) {
                Assert.fail(throwable.getMessage());
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
    public void testHandleRequestError() {
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
                Assert.assertTrue(res instanceof ErrorResponse);
                
                ErrorResponse errorResponse = (ErrorResponse) res;
                Assert.assertEquals(errorResponse.getErrorCode(), NacosException.SERVER_ERROR);
            }
            
            @Override
            public void onError(Throwable throwable) {
                Assert.fail(throwable.getMessage());
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
