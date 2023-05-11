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
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.Server;
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
import java.util.UUID;

/**
 * {@link GrpcBiStreamRequestAcceptor} unit test.
 *
 * @author chenglu
 * @date 2021-06-30 17:11
 */
@RunWith(MockitoJUnitRunner.class)
public class GrpcBiStreamRequestAcceptorTest {
    
    @Rule
    public GrpcCleanupRule grpcCleanupRule = new GrpcCleanupRule();
    
    public BiRequestStreamGrpc.BiRequestStreamStub streamStub;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @InjectMocks
    private GrpcBiStreamRequestAcceptor acceptor;
    
    private StreamObserver<Payload> payloadStreamObserver;
    
    private String connectId = UUID.randomUUID().toString();
    
    private String requestId = UUID.randomUUID().toString();
    
    @Before
    public void setUp() throws IOException {
        PayloadRegistry.init();
        String serverName = InProcessServerBuilder.generateName();
        String remoteIp = "127.0.0.1";
        Server mockServer = InProcessServerBuilder
                .forName(serverName).directExecutor().addService(acceptor)
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
                })
                .build();
        grpcCleanupRule.register(mockServer.start());
        streamStub = BiRequestStreamGrpc.newStub(grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
        Mockito.doReturn(true).when(connectionManager).traced(Mockito.any());
    }
    
    @Test
    public void testConnectionSetupRequest() {
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                System.out.println("Receive data from server, data: " + payload);
                Assert.assertNotNull(payload);
                ConnectResetRequest connectResetRequest = (ConnectResetRequest) GrpcUtils.parse(payload);
                Response response = new ConnectResetResponse();
                response.setRequestId(connectResetRequest.getRequestId());
                Payload res = GrpcUtils.convert(response);
                payloadStreamObserver.onNext(res);
                payloadStreamObserver.onCompleted();
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
