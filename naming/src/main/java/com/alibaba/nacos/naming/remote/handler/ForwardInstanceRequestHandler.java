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

package com.alibaba.nacos.naming.remote.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.response.InstanceResponse;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.cluster.remote.ClusterConnection;
import com.alibaba.nacos.naming.cluster.remote.request.ForwardInstanceRequest;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.remote.RemotingConnectionHolder;
import org.springframework.stereotype.Component;

import java.util.HashMap;

/**
 * Forward instance request handler.
 *
 * @author xiweng.yy
 */
@Component
public class ForwardInstanceRequestHandler extends RequestHandler<ForwardInstanceRequest, InstanceResponse> {
    
    private final InstanceRequestHandler instanceRequestHandler;
    
    private final RemotingConnectionHolder remotingConnectionHolder;
    
    private final DistroMapper distroMapper;
    
    public ForwardInstanceRequestHandler(InstanceRequestHandler instanceRequestHandler,
            RemotingConnectionHolder remotingConnectionHolder, DistroMapper distroMapper) {
        this.instanceRequestHandler = instanceRequestHandler;
        this.remotingConnectionHolder = remotingConnectionHolder;
        this.distroMapper = distroMapper;
    }
    
    @Override
    public InstanceResponse handle(ForwardInstanceRequest request, RequestMeta meta) throws NacosException {
        ForwardInstanceRequest actualRequest = (ForwardInstanceRequest) request;
        InstanceRequest instanceRequest = actualRequest.getInstanceRequest();
        String serviceName = NamingUtils
                .getGroupedName(instanceRequest.getServiceName(), instanceRequest.getGroupName());
        if (distroMapper.responsible(serviceName)) {
            RequestMeta sourceRequestMeta = actualRequest.getSourceRequestMeta();
            addRemotingConnectionIfAbsent(sourceRequestMeta);
            return instanceRequestHandler.handle(instanceRequest, sourceRequestMeta);
        }
        throw new NacosException(NacosException.BAD_GATEWAY,
                String.format("Forward instance request to error server, service: %s", serviceName));
    }
    
    private void addRemotingConnectionIfAbsent(RequestMeta sourceRequestMeta) {
        if (null == remotingConnectionHolder.getRemotingConnection(sourceRequestMeta.getConnectionId())) {
            ConnectionMetaInfo metaInfo = new ConnectionMetaInfo(sourceRequestMeta.getConnectionId(),
                    sourceRequestMeta.getClientIp(), "cluster", sourceRequestMeta.getClientVersion(), new HashMap<>(2));
            remotingConnectionHolder.clientConnected(new ClusterConnection(metaInfo));
        }
    }
    
}
