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

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.env.NacosClientProperties;

import java.util.Properties;

/**
 * Nacos AI client service implementation.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosAiService implements AiService {
    
    private final AiGrpcClient grpcClient;
    
    public NacosAiService(Properties properties) throws NacosException {
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        this.grpcClient = new AiGrpcClient(clientProperties);
        start();
    }
    
    private void start() throws NacosException {
        this.grpcClient.start();
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String mcpName, String version) throws NacosException {
        // TODO get index from cache.
        String mcpId = grpcClient.indexMcpNameToMcpId(mcpName);
        return grpcClient.queryMcpServer(mcpId, version);
    }
    
    @Override
    public void shutdown() throws NacosException {
        this.grpcClient.shutdown();
    }
}
