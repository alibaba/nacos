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
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.response.InstanceResponse;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.cluster.remote.ClusterClientManager;
import com.alibaba.nacos.naming.cluster.remote.request.ForwardInstanceRequest;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.remote.RemotingConnectionHolder;
import org.springframework.stereotype.Component;

/**
 * Instance request handler.
 *
 * @author xiweng.yy
 */
@Component
public class InstanceRequestHandler extends RequestHandler<InstanceRequest, InstanceResponse> {
    
    private final ServiceManager serviceManager;
    
    private final RemotingConnectionHolder remotingConnectionHolder;
    
    private final ClusterClientManager clusterClientManager;
    
    private final DistroMapper distroMapper;
    
    public InstanceRequestHandler(ServiceManager serviceManager, RemotingConnectionHolder remotingConnectionHolder,
            ClusterClientManager clusterClientManager, DistroMapper distroMapper) {
        this.serviceManager = serviceManager;
        this.remotingConnectionHolder = remotingConnectionHolder;
        this.clusterClientManager = clusterClientManager;
        this.distroMapper = distroMapper;
    }
    
    @Override
    public InstanceResponse handle(InstanceRequest request, RequestMeta meta) throws NacosException {
        InstanceRequest instanceRequest = (InstanceRequest) request;
        String serviceName = NamingUtils
                .getGroupedName(instanceRequest.getServiceName(), instanceRequest.getGroupName());
        if (distroMapper.responsible(serviceName)) {
            return handleResponsibleRequest(serviceName, instanceRequest, meta);
        } else {
            return forwardRequestToResponsibleServer(serviceName, instanceRequest, meta);
        }
    }
    
    private InstanceResponse handleResponsibleRequest(String serviceName, InstanceRequest request, RequestMeta meta)
            throws NacosException {
        String namespace = request.getNamespace();
        switch (request.getType()) {
            case NamingRemoteConstants.REGISTER_INSTANCE:
                return registerInstance(namespace, serviceName, request, meta);
            case NamingRemoteConstants.DE_REGISTER_INSTANCE:
                return deregisterInstance(namespace, serviceName, request, meta);
            default:
                throw new NacosException(NacosException.INVALID_PARAM,
                        String.format("Unsupported request type %s", request.getType()));
        }
    }
    
    private InstanceResponse forwardRequestToResponsibleServer(String serviceName, InstanceRequest request,
            RequestMeta meta) throws NacosException {
        String targetAddress = distroMapper.mapSrv(serviceName);
        if (clusterClientManager.hasClientForMember(targetAddress)) {
            return (InstanceResponse) clusterClientManager.getClusterClient(targetAddress)
                    .request(new ForwardInstanceRequest(request, meta));
        }
        throw new NacosException(NacosException.BAD_GATEWAY,
                String.format("Can't find responsible server for service %s", serviceName));
    }
    
    private InstanceResponse registerInstance(String namespace, String serviceName, InstanceRequest instanceRequest,
            RequestMeta meta) throws NacosException {
        if (!serviceManager.containService(namespace, serviceName)) {
            serviceManager.createEmptyService(namespace, serviceName, false);
        }
        Instance instance = parseInstance(instanceRequest.getInstance());
        instance.setServiceName(serviceName);
        instance.setInstanceId(instance.generateInstanceId());
        instance.setLastBeat(System.currentTimeMillis());
        // Register instance by connection, do not need keep alive by beat.
        serviceManager.addInstance(namespace, serviceName, instance.isEphemeral(), instance);
        remotingConnectionHolder.getRemotingConnection(meta.getConnectionId())
                .addNewInstance(namespace, serviceName, instance);
        return new InstanceResponse(NamingRemoteConstants.REGISTER_INSTANCE);
    }
    
    private InstanceResponse deregisterInstance(String namespace, String serviceName, InstanceRequest instanceRequest,
            RequestMeta meta) throws NacosException {
        if (!serviceManager.containService(namespace, serviceName)) {
            Loggers.SRV_LOG.warn("remove instance from non-exist service: {}", serviceName);
            return new InstanceResponse(NamingRemoteConstants.DE_REGISTER_INSTANCE);
        }
        Instance instance = parseInstance(instanceRequest.getInstance());
        serviceManager.removeInstance(namespace, serviceName, instance.isEphemeral(), instance);
        remotingConnectionHolder.getRemotingConnection(meta.getConnectionId())
                .removeInstance(namespace, serviceName, instance);
        return new InstanceResponse(NamingRemoteConstants.DE_REGISTER_INSTANCE);
    }
    
    private Instance parseInstance(com.alibaba.nacos.api.naming.pojo.Instance instance) {
        Instance result = new Instance(instance.getIp(), instance.getPort());
        result.setClusterName(StringUtils.isBlank(instance.getClusterName()) ? UtilsAndCommons.DEFAULT_CLUSTER_NAME
                : instance.getClusterName());
        result.setEnabled(instance.isEnabled());
        result.setEphemeral(instance.isEphemeral());
        result.setWeight(instance.getWeight());
        result.setMetadata(instance.getMetadata());
        return result;
    }
    
}
