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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.ai.event.McpServerChangeNotifier;
import com.alibaba.nacos.client.ai.event.McpServerChangedEvent;
import com.alibaba.nacos.client.ai.event.McpServerListenerInvoker;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Properties;

/**
 * Nacos AI client service implementation.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosAiService implements AiService {
    
    private final String namespaceId;
    
    private final AiGrpcClient grpcClient;
    
    private final NacosMcpServerCacheHolder cacheHolder;
    
    private final McpServerChangeNotifier mcpServerNotifier;
    
    public NacosAiService(Properties properties) throws NacosException {
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        this.namespaceId = initNamespace(clientProperties);
        this.grpcClient = new AiGrpcClient(namespaceId, clientProperties);
        this.cacheHolder = new NacosMcpServerCacheHolder(grpcClient, clientProperties);
        this.mcpServerNotifier = new McpServerChangeNotifier();
        start();
    }
    
    private String initNamespace(NacosClientProperties properties) {
        String tempNamespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        if (StringUtils.isBlank(tempNamespace)) {
            return Constants.DEFAULT_NAMESPACE_ID;
        }
        return tempNamespace;
    }
    
    private void start() throws NacosException {
        this.grpcClient.start(this.cacheHolder);
        NotifyCenter.registerToPublisher(McpServerChangedEvent.class, 16384);
        NotifyCenter.registerSubscriber(this.mcpServerNotifier);
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String mcpName, String version) throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `mcpName` not present");
        }
        return grpcClient.queryMcpServer(mcpName, version);
    }
    
    @Override
    public String releaseMcpServer(McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification)
            throws NacosException {
        if (null == serverSpecification) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification` not present");
        }
        if (StringUtils.isBlank(serverSpecification.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification.name` not present");
        }
        if (null == serverSpecification.getVersionDetail() || StringUtils.isBlank(
                serverSpecification.getVersionDetail().getVersion())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification.versionDetail.version` not present");
        }
        return grpcClient.releaseMcpServer(serverSpecification, toolSpecification);
    }
    
    @Override
    public void registerMcpServerEndpoint(String mcpName, String address, int port, String version)
            throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        Instance instance = new Instance();
        instance.setIp(address);
        instance.setPort(port);
        instance.validate();
        grpcClient.registerMcpServerEndpoint(mcpName, address, port, version);
    }
    
    @Override
    public void deregisterMcpServerEndpoint(String mcpName, String address, int port) throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        Instance instance = new Instance();
        instance.setIp(address);
        instance.setPort(port);
        instance.validate();
        grpcClient.deregisterMcpServerEndpoint(mcpName, address, port);
    }
    
    @Override
    public McpServerDetailInfo subscribeMcpServer(String mcpName, AbstractNacosMcpServerListener mcpServerListener)
            throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        if (null == mcpServerListener) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpServerListener` can't be empty or null");
        }
        McpServerListenerInvoker listenerInvoker = new McpServerListenerInvoker(mcpServerListener);
        mcpServerNotifier.registerListener(mcpName, listenerInvoker);
        McpServerDetailInfo result = grpcClient.subscribeMcpServer(mcpName);
        if (!listenerInvoker.isInvoked()) {
            listenerInvoker.invoke(new NacosMcpServerEvent(result));
        }
        return result;
    }
    
    @Override
    public void unsubscribeMcpServer(String mcpName, AbstractNacosMcpServerListener mcpServerListener)
            throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        if (null == mcpServerListener) {
            return;
        }
        McpServerListenerInvoker listenerInvoker = new McpServerListenerInvoker(mcpServerListener);
        mcpServerNotifier.deregisterListener(mcpName, listenerInvoker);
        if (!mcpServerNotifier.isSubscribed(mcpName)) {
            grpcClient.unsubscribeMcpServer(mcpName);
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        this.grpcClient.shutdown();
        this.cacheHolder.shutdown();
    }
}
