/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.remote.RpcServer;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerTransportFilter;
import io.grpc.internal.ServerStream;
import io.grpc.internal.ServerStreamHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.SocketAddress;
import java.util.UUID;

/**
 * Grpc implementation as  a rpc server.
 *
 * @author liuzunfei
 * @version $Id: GrpcServer.java, v 0.1 2020年07月13日 3:42 PM liuzunfei Exp $
 */
@Service
public class GrpcServer extends RpcServer {
    
    private static final int PORT_OFFSET = 1000;
    
    private Server server;
    
    @Autowired
    private GrpcStreamRequestHanderImpl streamRequestHander;
    
    @Autowired
    private GrpcRequestHandlerReactor requestHander;
    
    @Autowired
    private RequestHandlerRegistry requestHandlerRegistry;
    
    int grpcServerPort = ApplicationUtils.getPort() + rpcPortOffset();
    
    private void init() {
    }
    
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.GRPC;
    }
    
    @Override
    public void startServer() throws Exception {
        init();
        server = ServerBuilder.forPort(grpcServerPort).addService(streamRequestHander).addService(requestHander)
                .addTransportFilter(new ServerTransportFilter() {
                    @Override
                    public Attributes transportReady(Attributes transportAttrs) {
                        System.out.println("transportReady:" + transportAttrs);
                        Attributes test = transportAttrs.toBuilder().set(key, UUID.randomUUID().toString()).build();
                        return test;
                    }
            
                    @Override
                    public void transportTerminated(Attributes transportAttrs) {
                        System.out.println("transportTerminated:" + transportAttrs);
                        super.transportTerminated(transportAttrs);
                    }
                }).intercept(new ConnetionIntereptor()).build();
        server.start();
    }
    
    @Override
    public int rpcPortOffset() {
        return PORT_OFFSET;
    }
    
    @Override
    public void shundownServer() {
        if (server != null) {
            server.shutdown();
        }
    }
    
    static final Attributes.Key key = Attributes.Key.create("conn_id");
    
    static class ConnetionIntereptor implements ServerInterceptor {
        
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            Context ctx = Context.current();
            // System.out.println(build);
            System.out.println(call.getAttributes().get(key).toString());
            return Contexts.interceptCall(Context.current(), call, headers, next);
            
        }
    }
}
