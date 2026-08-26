/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.model.mcp.registry.KeyValueInput;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Projects the current MCP Naming model into backend and frontend endpoint responses.
 *
 * <p>This component deliberately preserves the existing Direct, REF and BACKEND behavior. It is
 * shared by the legacy Config read path and the lifecycle descriptor read path so the migration
 * does not introduce a second endpoint interpretation.</p>
 *
 * @author Nacos
 */
@Service
public class McpEndpointReadService {
    
    private final McpEndpointOperationService endpointOperationService;
    
    public McpEndpointReadService(McpEndpointOperationService endpointOperationService) {
        this.endpointOperationService = endpointOperationService;
    }
    
    /**
     * Resolve and attach backend and frontend endpoints to one remote MCP detail response.
     *
     * @param detailInfo MCP detail loaded from its persisted Server content
     * @throws NacosException when a referenced Naming service cannot be queried
     */
    public void injectEndpoint(McpServerDetailInfo detailInfo) throws NacosException {
        injectBackendEndpointRef(detailInfo);
        injectFrontendEndpointRef(detailInfo);
    }
    
    private void injectBackendEndpointRef(McpServerDetailInfo detailInfo) throws NacosException {
        McpServiceRef serviceRef = detailInfo.getRemoteServerConfig().getServiceRef();
        List<Instance> instances = endpointOperationService.getMcpServerEndpointInstances(
            serviceRef);
        String protocol = Objects.isNull(serviceRef) ? null : serviceRef.getTransportProtocol();
        List<McpEndpointInfo> backendEndpoints = transferToMcpEndpointInfo(instances,
            detailInfo.getRemoteServerConfig().getExportPath(), protocol);
        detailInfo.setBackendEndpoints(backendEndpoints);
    }
    
    private List<McpEndpointInfo> transferToMcpEndpointInfoWithHeaders(List<Instance> instances,
        String exportPath, String protocol, List<KeyValueInput> headers) {
        List<McpEndpointInfo> endpointInfos = new LinkedList<>();
        for (Instance each : instances) {
            McpEndpointInfo endpointInfo = new McpEndpointInfo();
            endpointInfo.setAddress(each.getIp());
            endpointInfo.setPort(each.getPort());
            endpointInfo.setProtocol(protocol);
            endpointInfo.setHeaders(headers);
            endpointInfo.setPath(exportPath);
            endpointInfos.add(endpointInfo);
        }
        return endpointInfos;
    }
    
    private List<McpEndpointInfo> transferToMcpEndpointInfo(List<Instance> instances,
        String exportPath, String protocol) {
        return transferToMcpEndpointInfoWithHeaders(instances, exportPath, protocol, null);
    }
    
    private void injectFrontendEndpointRef(McpServerDetailInfo detailInfo) throws NacosException {
        List<FrontEndpointConfig> frontEndpointConfigs = detailInfo.getRemoteServerConfig()
            .getFrontEndpointConfigList();
        if (CollectionUtils.isEmpty(frontEndpointConfigs)) {
            detailInfo.setFrontendEndpoints(Collections.emptyList());
            return;
        }
        List<McpEndpointInfo> frontendEndpoints = new LinkedList<>();
        for (FrontEndpointConfig each : frontEndpointConfigs) {
            if (AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF.equals(each.getEndpointType())) {
                addReferencedFrontendEndpoints(each, frontendEndpoints);
            } else if (AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT.equals(each.getEndpointType())) {
                frontendEndpoints.add(toDirectFrontendEndpoint(each));
            } else if (AiConstants.Mcp.MCP_FRONT_ENDPOINT_TYPE_TO_BACK
                .equals(each.getEndpointType())) {
                addBackendFrontendEndpoints(detailInfo, each, frontendEndpoints);
            }
        }
        detailInfo.setFrontendEndpoints(frontendEndpoints);
    }
    
    private void addReferencedFrontendEndpoints(FrontEndpointConfig config,
        List<McpEndpointInfo> frontendEndpoints) throws NacosException {
        McpServiceRef serviceRef = McpRequestUtil.transferToMcpServiceRef(
            config.getEndpointData());
        List<Instance> instances = endpointOperationService.getMcpServerEndpointInstances(
            serviceRef);
        frontendEndpoints.addAll(transferToMcpEndpointInfoWithHeaders(instances, config.getPath(),
            config.getProtocol(), config.getHeaders()));
    }
    
    private McpEndpointInfo toDirectFrontendEndpoint(FrontEndpointConfig config) {
        McpEndpointInfo result = new McpEndpointInfo();
        result.setPath(config.getPath());
        result.setProtocol(config.getProtocol());
        result.setHeaders(config.getHeaders());
        String address = config.getEndpointData().toString();
        if (InternetAddressUtil.containsPort(address)) {
            String[] info = InternetAddressUtil.splitIpPortStr(address);
            result.setAddress(info[0]);
            result.setPort(Integer.parseInt(info[1]));
        } else {
            result.setAddress(address);
            result.setPort(Constants.PROTOCOL_TYPE_HTTP.equals(config.getProtocol()) ? 80 : 443);
        }
        return result;
    }
    
    private void addBackendFrontendEndpoints(McpServerDetailInfo detailInfo,
        FrontEndpointConfig config, List<McpEndpointInfo> frontendEndpoints) {
        detailInfo.getBackendEndpoints().stream().map(endpoint -> {
            McpEndpointInfo result = new McpEndpointInfo();
            result.setAddress(endpoint.getAddress());
            result.setPort(endpoint.getPort());
            result.setProtocol(endpoint.getProtocol());
            result.setPath(config.getPath());
            result.setHeaders(config.getHeaders());
            return result;
        }).forEach(frontendEndpoints::add);
    }
}
