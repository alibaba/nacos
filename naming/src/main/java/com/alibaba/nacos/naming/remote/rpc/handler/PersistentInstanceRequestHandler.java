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

package com.alibaba.nacos.naming.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.PersistentInstanceRequest;
import com.alibaba.nacos.api.naming.remote.response.InstanceResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.DeregisterInstanceReason;
import com.alibaba.nacos.common.trace.event.naming.DeregisterInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterInstanceTraceEvent;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.PersistentInstanceRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl;
import com.alibaba.nacos.naming.utils.InstanceUtil;
import com.alibaba.nacos.naming.utils.NamingRequestUtil;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import org.springframework.stereotype.Component;

/**
 * Persistent instance request handler.
 *
 * @author blake.qiu
 */
@Component
public class PersistentInstanceRequestHandler extends RequestHandler<PersistentInstanceRequest, InstanceResponse> {
    
    private final PersistentClientOperationServiceImpl clientOperationService;
    
    public PersistentInstanceRequestHandler(PersistentClientOperationServiceImpl clientOperationService) {
        this.clientOperationService = clientOperationService;
    }
    
    @Override
    @TpsControl(pointName = "RemoteNamingInstanceRegisterDeregister", name = "RemoteNamingInstanceRegisterDeregister")
    @Secured(action = ActionTypes.WRITE)
    @ExtractorManager.Extractor(rpcExtractor = PersistentInstanceRequestParamExtractor.class)
    public InstanceResponse handle(PersistentInstanceRequest request, RequestMeta meta) throws NacosException {
        Service service = Service.newService(request.getNamespace(), request.getGroupName(), request.getServiceName(),
                false);
        InstanceUtil.setInstanceIdIfEmpty(request.getInstance(), service.getGroupedServiceName());
        switch (request.getType()) {
            case NamingRemoteConstants.REGISTER_INSTANCE:
                return registerInstance(service, request, meta);
            case NamingRemoteConstants.DE_REGISTER_INSTANCE:
                return deregisterInstance(service, request, meta);
            default:
                throw new NacosException(NacosException.INVALID_PARAM,
                        String.format("Unsupported request type %s", request.getType()));
        }
    }
    
    private InstanceResponse registerInstance(Service service, PersistentInstanceRequest request, RequestMeta meta) {
        Instance instance = request.getInstance();
        String clientId = IpPortBasedClient.getClientId(instance.toInetAddr(), false);
        clientOperationService.registerInstance(service, instance, clientId);
        NotifyCenter.publishEvent(new RegisterInstanceTraceEvent(System.currentTimeMillis(),
                NamingRequestUtil.getSourceIpForGrpcRequest(meta), true, service.getNamespace(), service.getGroup(),
                service.getName(), instance.getIp(), instance.getPort()));
        return new InstanceResponse(NamingRemoteConstants.REGISTER_INSTANCE);
    }
    
    private InstanceResponse deregisterInstance(Service service, PersistentInstanceRequest request, RequestMeta meta) {
        Instance instance = request.getInstance();
        String clientId = IpPortBasedClient.getClientId(instance.toInetAddr(), false);
        clientOperationService.deregisterInstance(service, instance, clientId);
        NotifyCenter.publishEvent(new DeregisterInstanceTraceEvent(System.currentTimeMillis(),
                NamingRequestUtil.getSourceIpForGrpcRequest(meta), true, DeregisterInstanceReason.REQUEST,
                service.getNamespace(), service.getGroup(), service.getName(), instance.getIp(), instance.getPort()));
        return new InstanceResponse(NamingRemoteConstants.DE_REGISTER_INSTANCE);
    }
}
