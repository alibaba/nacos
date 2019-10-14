package com.alibaba.nacos.istio.mcp;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;


/**
 * @author nkorange
 * @since 1.1.4
 */
@Service
public class NacosMcpServer {

    private int port = 18848;
    private Server server;

    @Autowired
    private McpServerIntercepter intercepter;

    @Autowired
    private NacosMcpService nacosMcpService;

    @PostConstruct
    public void start() throws IOException {

        System.out.println("Starting Nacos MCP server...");

        server = ServerBuilder.forPort(port)
            .addService(ServerInterceptors.intercept(nacosMcpService, intercepter))
            .build();
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {

                System.out.println("Stopping Nacos MCP server...");
                NacosMcpServer.this.stop();
                System.out.println("Nacos MCP server stopped...");
            }
        });
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void waitForTerminated() throws InterruptedException {
        server.awaitTermination();
    }
}
