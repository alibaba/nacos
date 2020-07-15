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

package com.alibaba.nacos.naming.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.response.InstanceResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Instance request handler.
 *
 * @author xiweng.yy
 */
@Component
public class InstanceRequestHandler extends RequestHandler<InstanceRequest> {
    
    @Autowired
    private ServiceManager serviceManager;
    
    @Override
    public InstanceRequest parseBodyString(String bodyString) {
        return JacksonUtils.toObj(bodyString, InstanceRequest.class);
    }
    
    @Override
    public Response handle(Request request, RequestMeta meta) throws NacosException {
        InstanceRequest instanceRequest = (InstanceRequest) request;
        String namespace = instanceRequest.getNamespace();
        String serviceName = instanceRequest.getServiceName();
        switch (instanceRequest.getType()) {
            case NamingRemoteConstants.REGISTER_INSTANCE:
                if (!serviceManager.containService(namespace, serviceName)) {
                    serviceManager.createEmptyService(namespace, serviceName, false);
                }
                Instance instance = parseInstance(instanceRequest.getInstance());
                instance.setServiceName(serviceName);
                instance.setInstanceId(instance.generateInstanceId());
                instance.setLastBeat(System.currentTimeMillis());
                // Register instance by connection, do not need keep alive by beat.
                instance.setMarked(true);
                instance.validate();
                serviceManager.addInstance(namespace, serviceName, instance.isEphemeral(), instance);
                break;
            case NamingRemoteConstants.DE_REGISTER_INSTANCE:
                if (!serviceManager.containService(namespace, serviceName)) {
                    Loggers.SRV_LOG.warn("remove instance from non-exist service: {}", serviceName);
                    return new InstanceResponse(200, "success", request.getType());
                }
                instance = parseInstance(instanceRequest.getInstance());
                serviceManager.removeInstance(namespace, serviceName, instance.isEphemeral(), instance);
                break;
            default:
                throw new NacosException(NacosException.INVALID_PARAM, String.format("Unsupported request type %s", instanceRequest.getType()));
        }
        return new InstanceResponse(200, "success", request.getType());
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
    
    @Override
    public List<String> getRequestTypes() {
        return Lists.newArrayList(NamingRemoteConstants.REGISTER_INSTANCE,
                NamingRemoteConstants.DE_REGISTER_INSTANCE);
    }
}
