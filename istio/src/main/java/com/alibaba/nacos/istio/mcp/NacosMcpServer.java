/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.istio.mcp;

import com.alibaba.nacos.istio.misc.IstioConfig;
import com.alibaba.nacos.istio.misc.Loggers;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;

/**
 * Nacos MCP server.
 *
 * <p>This MCP serves as a ResourceSource defined by Istio.
 *
 * @author nkorange
 * @since 1.1.4
 */
@Service
public class NacosMcpServer {
    
    private final int port = 18848;
    
    private Server server;
    
    @Autowired
    private IstioConfig istioConfig;
    
    @Autowired
    private McpServerIntercepter intercepter;
    
    @Autowired
    private NacosMcpService nacosMcpService;
    
    @Autowired
    private NacosMcpOverXdsService nacosMcpOverXdsService;
    
    @Autowired
    private NacosToMcpResources nacosToMcpResources;
    
    /**
     * Start.
     *
     * @throws IOException io exception
     */
    @PostConstruct
    public void start() throws IOException {
        
        if (!istioConfig.isMcpServerEnabled()) {
            return;
        }
        
        Loggers.MAIN.info("MCP server, starting Nacos MCP server...");
        
        server = ServerBuilder.forPort(port).addService(ServerInterceptors.intercept(nacosMcpService, intercepter))
                .addService(ServerInterceptors.intercept(nacosMcpOverXdsService, intercepter)).build();
        server.start();
        nacosToMcpResources.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                
                System.out.println("Stopping Nacos MCP server...");
                NacosMcpServer.this.stop();
                System.out.println("Nacos MCP server stopped...");
            }
        });
    }
    
    /**
     * Stop.
     */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
}
