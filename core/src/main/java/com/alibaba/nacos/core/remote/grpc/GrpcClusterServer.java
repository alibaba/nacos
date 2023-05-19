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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Grpc implementation as  a rpc server.
 *
 * @author liuzunfei
 * @version $Id: BaseGrpcServer.java, v 0.1 2020年07月13日 3:42 PM liuzunfei Exp $
 */
@Service
public class GrpcClusterServer extends BaseGrpcServer {
    
    @Override
    public int rpcPortOffset() {
        return Constants.CLUSTER_GRPC_PORT_DEFAULT_OFFSET;
    }
    
    @Override
    public ThreadPoolExecutor getRpcExecutor() {
        if (!GlobalExecutor.clusterRpcExecutor.allowsCoreThreadTimeOut()) {
            GlobalExecutor.clusterRpcExecutor.allowCoreThreadTimeOut(true);
        }
        return GlobalExecutor.clusterRpcExecutor;
    }
    
    @Override
    protected long getKeepAliveTime() {
        Long property = EnvUtil.getProperty(GrpcServerConstants.GrpcConfig.CLUSTER_KEEP_ALIVE_TIME_PROPERTY,
                Long.class);
        if (property != null) {
            return property;
        }
        return super.getKeepAliveTime();
    }
    
    @Override
    protected long getKeepAliveTimeout() {
        Long property = EnvUtil.getProperty(GrpcServerConstants.GrpcConfig.CLUSTER_KEEP_ALIVE_TIMEOUT_PROPERTY,
                Long.class);
        if (property != null) {
            return property;
        }
        return super.getKeepAliveTimeout();
    }
    
    @Override
    protected long getPermitKeepAliveTime() {
        Long property = EnvUtil.getProperty(GrpcServerConstants.GrpcConfig.CLUSTER_PERMIT_KEEP_ALIVE_TIME,
                Long.class);
        if (property != null) {
            return property;
        }
        return super.getPermitKeepAliveTime();
    }
    
    @Override
    protected int getMaxInboundMessageSize() {
        Integer property = EnvUtil.getProperty(GrpcServerConstants.GrpcConfig.CLUSTER_MAX_INBOUND_MSG_SIZE_PROPERTY,
                Integer.class);
        if (property != null) {
            return property;
        }
        
        int size = super.getMaxInboundMessageSize();
        if (Loggers.REMOTE.isWarnEnabled()) {
            Loggers.REMOTE.warn("Recommended use '{}' property instead '{}', now property value is {}",
                    GrpcServerConstants.GrpcConfig.CLUSTER_MAX_INBOUND_MSG_SIZE_PROPERTY,
                    GrpcServerConstants.GrpcConfig.MAX_INBOUND_MSG_SIZE_PROPERTY, size);
        }
        return size;
    }
    
}
