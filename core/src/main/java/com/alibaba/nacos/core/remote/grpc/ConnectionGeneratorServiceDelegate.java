/*
 *
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

/**
 * ConnectionGeneratorServiceDelegate.
 *
 * @author jianwei.wjw
 */

public class ConnectionGeneratorServiceDelegate {
    private String connectionGeneratorType = System.getProperty("nacos.core.remote.connection.generator", "nacos");
    
    private ConnectionGeneratorService connectionGeneratorService = null;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionGeneratorServiceDelegate.class);
    
    private ConnectionGeneratorServiceDelegate() {
        for (ConnectionGeneratorService connectionGeneratorService : NacosServiceLoader.load(ConnectionGeneratorService.class)) {
            if (connectionGeneratorService.getType().equals(connectionGeneratorType)) {
                this.connectionGeneratorService = connectionGeneratorService;
                LoggerUtils.printIfInfoEnabled(LOGGER, "{} has been loaded, class: {}",
                        connectionGeneratorType, connectionGeneratorService.getClass().getName());
            }
        }
        
        if (Objects.isNull(connectionGeneratorService)) {
            throw new RuntimeException("can not find implementation of "
                    + ConnectionGeneratorService.class.getName() + " for type " + connectionGeneratorType);
        }
    }
    
    private static final ConnectionGeneratorServiceDelegate INSTANCE = new ConnectionGeneratorServiceDelegate();
    
    public static ConnectionGeneratorServiceDelegate getInstance() {
        return INSTANCE;
    }
    
    public Connection getConnection(ConnectionMeta metaInfo, StreamObserver streamObserver, Channel channel) {
        return connectionGeneratorService.getConnection(metaInfo, streamObserver, channel);
    }
}
