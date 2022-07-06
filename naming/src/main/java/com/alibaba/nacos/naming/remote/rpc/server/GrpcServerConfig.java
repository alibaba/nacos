package com.alibaba.nacos.naming.remote.rpc.server;

import com.alibaba.nacos.core.remote.grpc.BaseGrpcServer;
import com.alibaba.nacos.core.remote.grpc.GrpcClusterServer;
import com.alibaba.nacos.core.remote.grpc.GrpcSdkServer;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Config for GrpcServer
 *
 * @author Jiangnan Jia
 **/
@Configuration
public class GrpcServerConfig {

    @Bean
    public BaseGrpcServer grpcServer() {
        return EnvUtil.getStandaloneMode() ? new GrpcSdkServer() : new GrpcClusterServer();
    }

}
