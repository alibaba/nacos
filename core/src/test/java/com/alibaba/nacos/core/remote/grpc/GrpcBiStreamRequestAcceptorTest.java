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

import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ConnectResetResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.PayloadRegistry;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link GrpcBiStreamRequestAcceptor} unit test.
 *
 * @author chenglu
 * @date 2021-06-30 17:11
 */
@ExtendWith({MockitoExtension.class, GrpcCleanupExtension.class})
public class GrpcBiStreamRequestAcceptorTest {
    
    public BiRequestStreamGrpc.BiRequestStreamStub streamStub;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @InjectMocks
    private GrpcBiStreamRequestAcceptor acceptor;
    
    private StreamObserver<Payload> payloadStreamObserver;
    
    private String connectId = UUID.randomUUID().toString();
    
    private String requestId = UUID.randomUUID().toString();
    
    @BeforeEach
    void setUp(Resources resources) throws IOException {
        PayloadRegistry.init();
        String serverName = InProcessServerBuilder.generateName();
        String remoteIp = "127.0.0.1";
        Server mockServer = InProcessServerBuilder.forName(serverName).directExecutor().addService(acceptor)
                .intercept(new ServerInterceptor() {
                    @Override
                    public <R, S> ServerCall.Listener<R> interceptCall(ServerCall<R, S> serverCall, Metadata metadata,
                            ServerCallHandler<R, S> serverCallHandler) {
                        Context ctx = Context.current().withValue(GrpcServerConstants.CONTEXT_KEY_CONN_ID, UUID.randomUUID().toString())
                                .withValue(GrpcServerConstants.CONTEXT_KEY_CONN_LOCAL_PORT, 1234)
                                .withValue(GrpcServerConstants.CONTEXT_KEY_CONN_REMOTE_PORT, 8948)
                                .withValue(GrpcServerConstants.CONTEXT_KEY_CONN_REMOTE_IP, remoteIp);
                        return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
                    }
                }).build();
        resources.register(mockServer.start(), Duration.ofSeconds(20));
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        resources.register(channel, Duration.ofSeconds(20L));
        streamStub = BiRequestStreamGrpc.newStub(channel);
        Mockito.doReturn(true).when(connectionManager).traced(Mockito.any());
    }
    
    @Test
    void testConnectionSetupRequest() {
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                System.out.println("Receive data from server, data: " + payload);
                assertNotNull(payload);
                ConnectResetRequest connectResetRequest = (ConnectResetRequest) GrpcUtils.parse(payload);
                Response response = new ConnectResetResponse();
                response.setRequestId(connectResetRequest.getRequestId());
                Payload res = GrpcUtils.convert(response);
                payloadStreamObserver.onNext(res);
                payloadStreamObserver.onCompleted();
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
        payloadStreamObserver = streamStub.requestBiStream(streamObserver);
        RequestMeta metadata = new RequestMeta();
        metadata.setClientIp("127.0.0.1");
        metadata.setConnectionId(connectId);
        
        ConnectionSetupRequest connectionSetupRequest = new ConnectionSetupRequest();
        connectionSetupRequest.setRequestId(requestId);
        connectionSetupRequest.setClientVersion("2.0.3");
        Payload payload = GrpcUtils.convert(connectionSetupRequest, metadata);
        payloadStreamObserver.onNext(payload);
    }
}
