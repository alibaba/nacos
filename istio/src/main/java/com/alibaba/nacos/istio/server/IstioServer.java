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

package com.alibaba.nacos.istio.server;

import com.alibaba.nacos.istio.common.NacosResourceManager;
import com.alibaba.nacos.istio.mcp.NacosMcpService;
import com.alibaba.nacos.istio.misc.IstioConfig;
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.xds.NacosXdsService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;

/**
 * @author special.fy
 */
@Service
public class IstioServer {

    private Server server;

    @Autowired
    private IstioConfig istioConfig;

    @Autowired
    private ServerInterceptor serverInterceptor;

    @Autowired
    private NacosMcpService nacosMcpService;

    @Autowired
    private NacosXdsService nacosXdsService;

    @Autowired
    private NacosResourceManager nacosResourceManager;

    /**
     * Start.
     *
     * @throws IOException io exception
     */
    @PostConstruct
    public void start() throws IOException {

        if (!istioConfig.isServerEnabled()) {
            Loggers.MAIN.info("The Nacos Istio server is disabled.");
            return;
        }
        nacosResourceManager.start();

        Loggers.MAIN.info("Nacos Istio server, starting Nacos Istio server...");

        server = ServerBuilder.forPort(istioConfig.getServerPort()).addService(ServerInterceptors.intercept(nacosMcpService, serverInterceptor))
                .addService(ServerInterceptors.intercept(nacosXdsService, serverInterceptor)).build();
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {

                System.out.println("Stopping Nacos Istio server...");
                IstioServer.this.stop();
                System.out.println("Nacos Istio server stopped...");
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
