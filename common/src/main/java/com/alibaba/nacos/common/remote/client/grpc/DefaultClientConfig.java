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

package com.alibaba.nacos.common.remote.client.grpc;

import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.utils.ThreadUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Default grpc client config.
 *
 * @author karsonto
 */
public class DefaultClientConfig implements GrpcClientConfig {
    
    private String name;
    
    private ServerListFactory serverListFactory;
    
    private int retryTimes;
    
    private long timeOutMills;
    
    private long connectionKeepAlive;
    
    private long threadPoolKeepAlive;
    
    private int threadPoolCoreSize;
    
    private int threadPoolMaxSize;
    
    private long serverCheckTimeOut;
    
    private int threadPoolQueueSize;
    
    private int maxInboundMessageSize;
    
    private int channelKeepAlive;
    
    private int healthCheckRetryTimes;
    
    private long healthCheckTimeOut;
    
    private Map<String, String> labels;
    
    /**
     * constructor.
     *
     * @param builder builder of DefaultClientConfig builder.
     */
    public DefaultClientConfig(Builder builder) {
        this.name = builder.name;
        this.serverListFactory = builder.serverListFactory;
        this.retryTimes = builder.retryTimes;
        this.timeOutMills = builder.timeOutMills;
        this.connectionKeepAlive = builder.connectionKeepAlive;
        this.threadPoolKeepAlive = builder.threadPoolKeepAlive;
        this.threadPoolCoreSize = builder.threadPoolCoreSize;
        this.threadPoolMaxSize = builder.threadPoolMaxSize;
        this.serverCheckTimeOut = builder.serverCheckTimeOut;
        this.threadPoolQueueSize = builder.threadPoolQueueSize;
        this.maxInboundMessageSize = builder.maxInboundMessageSize;
        this.channelKeepAlive = builder.channelKeepAlive;
        this.healthCheckRetryTimes = builder.healthCheckRetryTimes;
        this.healthCheckTimeOut = builder.healthCheckTimeOut;
        this.labels = builder.labels;
        
    }
    
    @Override
    public String name() {
        return this.name;
    }
    
    @Override
    public ServerListFactory serverListFactory() {
        return this.serverListFactory;
    }
    
    @Override
    public int retryTimes() {
        return Integer.parseInt(
                System.getProperty(GrpcConstants.NACOS_CLIENT_GRPC_RETRY_TIMES, String.valueOf(this.retryTimes)));
    }
    
    @Override
    public long timeOutMills() {
        return Long.parseLong(
                System.getProperty(GrpcConstants.NACOS_CLIENT_GRPC_TIMEOUT_MILLS, String.valueOf(this.timeOutMills)));
    }
    
    @Override
    public long connectionKeepAlive() {
        return Long.parseLong(System.getProperty(GrpcConstants.NACOS_CLIENT_GRPC_CONNECT_KEEP_ALIVE_TIME,
                String.valueOf(this.channelKeepAlive)));
    }
    
    @Override
    public int threadPoolCoreSize() {
        return Integer.parseInt(System.getProperty(GrpcConstants.NACOS_CLIENT_GRPC_THREADPOOL_CORE_SIZE,
                String.valueOf(this.threadPoolCoreSize)));
    }
    
    @Override
    public int threadPoolMaxSize() {
        return Integer.parseInt(System.getProperty(GrpcConstants.NACOS_CLIENT_GRPC_THREADPOOL_MAX_SIZE,
                String.valueOf(this.threadPoolMaxSize)));
    }
    
    @Override
    public long threadPoolKeepAlive() {
        return Long.parseLong(System.getProperty(GrpcConstants.NACOS_CLIENT_GRPC_THREADPOOL_KEEPALIVETIME,
                String.valueOf(this.threadPoolKeepAlive)));
    }
    
    @Override
    public long serverCheckTimeOut() {
        return Long.parseLong(System.getProperty(GrpcConstants.NACOS_CLIENT_GRPC_SERVER_CHECK_TIMEOUT,
                String.valueOf(this.serverCheckTimeOut)));
    }
    
    @Override
    public int threadPoolQueueSize() {
        return Integer.parseInt(System.getProperty(GrpcConstants.NACOS_CLIENT_GRPC_QUEUESIZE,
                String.valueOf(this.threadPoolQueueSize)));
    }
    
    @Override
    public int maxInboundMessageSize() {
        return Integer.parseInt(
                System.getProperty(GrpcConstants.MAX_INBOUND_MESSAGE_SIZE, String.valueOf(this.maxInboundMessageSize)));
    }
    
    @Override
    public int channelKeepAlive() {
        return Integer.parseInt(
                System.getProperty(GrpcConstants.CHANNEL_KEEP_ALIVE_TIME, String.valueOf(this.channelKeepAlive)));
    }
    
    @Override
    public int healthCheckRetryTimes() {
        return Integer.parseInt(System.getProperty(GrpcConstants.NACOS_CLIENT_GRPC_HEALTHCHECK_RETRY_TIMES,
                String.valueOf(this.healthCheckRetryTimes)));
    }
    
    @Override
    public long healthCheckTimeOut() {
        return Integer.parseInt(System.getProperty(GrpcConstants.NACOS_CLIENT_GRPC_HEALTHCHECK_TIMEOUT,
                String.valueOf(this.healthCheckTimeOut)));
    }
    
    @Override
    public Map<String, String> labels() {
        return this.labels;
    }
    
    public static class Builder {
        
        private String name;
        
        private ServerListFactory serverListFactory;
        
        private int retryTimes = 3;
        
        private long timeOutMills = 3000L;
        
        private long connectionKeepAlive = 5000L;
        
        private long threadPoolKeepAlive = 10000L;
        
        private int threadPoolCoreSize = ThreadUtils.getSuitableThreadCount(2);
        
        private int threadPoolMaxSize = ThreadUtils.getSuitableThreadCount(8);
        
        private long serverCheckTimeOut = 3000L;
        
        private int threadPoolQueueSize = 10000;
        
        private int maxInboundMessageSize = 10 * 1024 * 1024;
        
        private int channelKeepAlive = 6 * 60 * 1000;
        
        private int healthCheckRetryTimes = 1;
        
        private long healthCheckTimeOut = 3000L;
        
        private Map<String, String> labels = new HashMap<>();
        
        /**
         * set client name.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        /**
         * set serverListFactory.
         */
        public Builder serverListFactory(ServerListFactory serverListFactory) {
            this.serverListFactory = serverListFactory;
            return this;
        }
        
        /**
         * set retryTimes.
         */
        public Builder retryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
            return this;
        }
    
        /**
         * set timeOutMills.
         */
        public Builder timeOutMills(long timeOutMills) {
            this.timeOutMills = timeOutMills;
            return this;
        }
    
        /**
         * set connectionKeepAlive.
         */
        public Builder connectionKeepAlive(long connectionKeepAlive) {
            this.connectionKeepAlive = connectionKeepAlive;
            return this;
        }
        
        /**
         * set threadPoolKeepAlive.
         */
        public Builder threadPoolKeepAlive(Long threadPoolKeepAlive) {
            this.threadPoolKeepAlive = threadPoolKeepAlive;
            return this;
        }
        
        /**
         * set threadPoolCoreSize.
         */
        public Builder threadPoolCoreSize(Integer threadPoolCoreSize) {
            if (!Objects.isNull(threadPoolCoreSize)) {
                this.threadPoolCoreSize = threadPoolCoreSize;
            }
            return this;
        }
        
        /**
         * set threadPoolMaxSize.
         */
        public Builder threadPoolMaxSize(Integer threadPoolMaxSize) {
            if (!Objects.isNull(threadPoolMaxSize)) {
                this.threadPoolMaxSize = threadPoolMaxSize;
            }
            return this;
        }
    
        /**
         * set serverCheckTimeOut.
         */
        public Builder serverCheckTimeOut(Long serverCheckTimeOut) {
            this.serverCheckTimeOut = serverCheckTimeOut;
            return this;
        }
    
        /**
         * set threadPoolQueueSize.
         */
        public Builder threadPoolQueueSize(int threadPoolQueueSize) {
            this.threadPoolQueueSize = threadPoolQueueSize;
            return this;
        }
    
        /**
         * set maxInboundMessageSize.
         */
        public Builder maxInboundMessageSize(int maxInboundMessageSize) {
            this.maxInboundMessageSize = maxInboundMessageSize;
            return this;
        }
    
        /**
         * set channelKeepAlive.
         */
        public Builder channelKeepAlive(int channelKeepAlive) {
            this.channelKeepAlive = channelKeepAlive;
            return this;
        }
    
        /**
         * set healthCheckRetryTimes.
         */
        public Builder healthCheckRetryTimes(int healthCheckRetryTimes) {
            this.healthCheckRetryTimes = healthCheckRetryTimes;
            return this;
        }
    
        /**
         * set healthCheckTimeOut.
         */
        public Builder healthCheckTimeOut(long healthCheckTimeOut) {
            this.healthCheckTimeOut = healthCheckTimeOut;
            return this;
        }
    
        /**
         * set labels.
         */
        public Builder labels(Map<String, String> labels) {
            this.labels.putAll(labels);
            return this;
        }
    
        /**
         * build GrpcClientConfig.
         */
        public GrpcClientConfig build() {
            return new DefaultClientConfig(this);
        }
    }
    
}
