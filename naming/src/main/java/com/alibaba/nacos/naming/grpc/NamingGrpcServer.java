package com.alibaba.nacos.naming.grpc;


import com.alibaba.nacos.core.remoting.grpc.impl.ConnectionGrpcIntercepter;
import com.alibaba.nacos.core.remoting.grpc.impl.ConnectionServiceGrpcImpl;
import com.alibaba.nacos.core.utils.Loggers;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Service
public class NamingGrpcServer {

    private int port = 18849;

    private Server server;

    @Autowired
    private NamingGrpcIntercepter namingGrpcIntercepter;

    @Autowired
    private ConnectionGrpcIntercepter connectionGrpcIntercepter;

    @Autowired
    private NamingServiceGrpcImpl namingGrpcService;

    @Autowired
    private ConnectionServiceGrpcImpl connectionServiceGrpc;

    @PostConstruct
    public void start() throws IOException {


        Loggers.GRPC.info("Starting Naming gRPC server...");

        server = ServerBuilder.forPort(port)
            .addService(ServerInterceptors.intercept(connectionServiceGrpc, connectionGrpcIntercepter))
            .addService(ServerInterceptors.intercept(namingGrpcService, namingGrpcIntercepter))
            .build();

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {

                System.out.println("Stopping Naming gRPC server...");
                NamingGrpcServer.this.stop();
                System.out.println("Naming gRPC server stopped...");
            }
        });
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
}
