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

import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.remote.RpcServer;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.Loggers;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

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
    
    @Autowired
    private ConnectionManager connectionManager;
    
    int grpcServerPort = ApplicationUtils.getPort() + rpcPortOffset();
    
    private void init() {
    }
    
    @PostConstruct
    @Override
    public void start() throws Exception {
        
        init();
        server = ServerBuilder.forPort(grpcServerPort).addService(streamRequestHander).addService(requestHander)
                .build();
        server.start();
        Loggers.RPC.info("Nacos gRPC  server  start successfully at port :" + grpcServerPort);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Loggers.RPC.info("Nacos gRPC server stopping...");
                GrpcServer.this.stop();
                Loggers.RPC.info("Nacos gRPC server stopped successfully...");
            }
        });
    
    }
    
    @Override
    public int rpcPortOffset() {
        return PORT_OFFSET;
    }
    
    @Override
    public void stop() {
        if (server != null) {
            Loggers.RPC.info("Nacos clear all rpc clients...");
            connectionManager.expelAll();
            try {
                //wait clients to switch  server.
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                //Do nothing.
            }
            server.shutdown();
        }
    }
}
