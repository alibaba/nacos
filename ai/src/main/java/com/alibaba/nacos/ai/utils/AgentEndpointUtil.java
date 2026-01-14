/*
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
 */

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Utils for Agent Endpoint of A2A.
 *
 * @author xiweng.yy
 */
public class AgentEndpointUtil {
    
    /**
     * Transfer a collection of AgentEndpoint to a list of Instance.
     *
     * @param endpoints the collection of AgentEndpoint to transfer
     * @return the list of Instance transferred from AgentEndpoint
     * @throws NacosApiException if any validation failed during the transfer process
     */
    public static List<Instance> transferToInstances(Collection<AgentEndpoint> endpoints) throws NacosApiException {
        List<Instance> result = new LinkedList<>();
        for (AgentEndpoint endpoint : endpoints) {
            result.add(transferToInstance(endpoint));
        }
        return result;
    }
    
    /**
     * Transfer a single AgentEndpoint to an Instance.
     *
     * @param endpoint the AgentEndpoint to transfer
     * @return the Instance transferred from AgentEndpoint
     * @throws NacosApiException if any validation failed during the transfer process
     */
    public static Instance transferToInstance(AgentEndpoint endpoint) throws NacosApiException {
        Instance instance = new Instance();
        instance.setIp(endpoint.getAddress());
        instance.setPort(endpoint.getPort());
        String path = StringUtils.isBlank(endpoint.getPath()) ? StringUtils.EMPTY : endpoint.getPath();
        String protocol = StringUtils.isBlank(endpoint.getProtocol()) ? StringUtils.EMPTY : endpoint.getProtocol();
        String query = StringUtils.isBlank(endpoint.getQuery()) ? StringUtils.EMPTY : endpoint.getQuery();
        Map<String, String> metadata = Map.of(Constants.A2A.AGENT_ENDPOINT_PATH_KEY, path,
                Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, endpoint.getTransport(),
                Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS, String.valueOf(endpoint.isSupportTls()),
                Constants.A2A.NACOS_AGENT_ENDPOINT_PROTOCOL_KEY, protocol, Constants.A2A.NACOS_AGENT_ENDPOINT_QUERY_KEY,
                query);
        instance.setMetadata(metadata);
        instance.validate();
        return instance;
    }
}