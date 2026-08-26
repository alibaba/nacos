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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.model.mcp.registry.KeyValueInput;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.naming.core.InstanceOperator;
import com.alibaba.nacos.naming.core.ServiceOperator;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Nacos AI MCP Endpoint operation service.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
public class McpEndpointOperationService {
    
    private final ServiceOperator serviceOperator;
    
    private final InstanceOperator instanceOperator;
    
    private final NamingMetadataManager metadataManager;
    
    public McpEndpointOperationService(ServiceOperator serviceOperator,
        InstanceOperator instanceOperator,
        NamingMetadataManager metadataManager) {
        this.serviceOperator = serviceOperator;
        this.instanceOperator = instanceOperator;
        this.metadataManager = metadataManager;
    }
    
    /**
     * Create Mcp Server Endpoint Service if necessary.
     *
     * <p>If type is REF, directly return service</p>
     * <p>If service not exist, do create new service and register instance, then return service</p>
     * <p>If service exist, only do register instance, then return service</p>
     *
     * @param namespaceId           namespace id of mcp server
     * @param mcpName               name of mcp server
     * @param endpointSpecification mcp server endpoint specification, see {@link McpEndpointSpec}
     * @param overrideExisting       if replace all the instances when update the mcp server
     * @return {@link Service}
     * @throws NacosException any exception during handling
     */
    public Service createMcpServerEndpointServiceIfNecessary(String namespaceId, String mcpName,
        String version,
        McpEndpointSpec endpointSpecification, boolean overrideExisting) throws NacosException {
        if (AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF
            .equalsIgnoreCase(endpointSpecification.getType())) {
            Map<String, String> endpointServiceData = endpointSpecification.getData();
            if (!endpointServiceData.containsKey(CommonParams.NAMESPACE_ID)
                || !endpointServiceData.containsKey(
                    CommonParams.GROUP_NAME)
                || !endpointServiceData.containsKey(CommonParams.SERVICE_NAME)) {
                throw new NacosApiException(NacosApiException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "`namespaceId`, `groupName`, `serviceName` should be in remoteServerConfig data if type is `REF`");
            }
            String refGroupName = endpointSpecification.getData().get(CommonParams.GROUP_NAME);
            String refServiceName = endpointSpecification.getData().get(CommonParams.SERVICE_NAME);
            return Service.newService(namespaceId, refGroupName, refServiceName);
        }
        String versionMcpName = mcpName + "::" + version;
        Service service = generateService(namespaceId, versionMcpName);
        if (isNotExist(service)) {
            doCreateNewService(service);
            doUpdateInstanceInfo(service, endpointSpecification, namespaceId, mcpName,
                overrideExisting, versionMcpName);
            return service;
        }
        doUpdateInstanceInfo(service, endpointSpecification, namespaceId, mcpName, overrideExisting,
            versionMcpName);
        return service;
    }
    
    public Service generateService(String namespaceId, String mcpName) {
        return Service.newService(namespaceId, Constants.MCP_SERVER_ENDPOINT_GROUP, mcpName);
    }
    
    public List<Instance> getMcpServerEndpointInstances(McpServiceRef serviceRef)
        throws NacosException {
        if (serviceRef == null) {
            return Collections.emptyList();
        }
        return instanceOperator.listInstance(serviceRef.getNamespaceId(), serviceRef.getGroupName(),
            serviceRef.getServiceName(), null, "", true).getHosts();
    }
    
    /**
     * Project the current MCP Naming model into backend and frontend endpoint responses.
     *
     * <p>This deliberately preserves the historical Direct, REF and BACKEND behavior. Both the
     * legacy Config implementation and the lifecycle implementation must use this method so the
     * lifecycle cutover cannot introduce a second endpoint interpretation.</p>
     *
     * @param detailInfo MCP detail loaded from persisted Server content
     * @throws NacosException when a referenced Naming service cannot be queried
     */
    public void injectEndpoint(McpServerDetailInfo detailInfo) throws NacosException {
        injectBackendEndpointRef(detailInfo);
        injectFrontendEndpointRef(detailInfo);
    }
    
    private void injectBackendEndpointRef(McpServerDetailInfo detailInfo) throws NacosException {
        if (detailInfo.getRemoteServerConfig() == null) {
            detailInfo.setBackendEndpoints(Collections.emptyList());
            return;
        }
        McpServiceRef serviceRef = detailInfo.getRemoteServerConfig().getServiceRef();
        List<Instance> instances = getMcpServerEndpointInstances(serviceRef);
        String protocol = Objects.isNull(serviceRef) ? null : serviceRef.getTransportProtocol();
        detailInfo.setBackendEndpoints(transferToMcpEndpointInfo(instances,
            detailInfo.getRemoteServerConfig().getExportPath(), protocol));
    }
    
    private List<McpEndpointInfo> transferToMcpEndpointInfoWithHeaders(List<Instance> instances,
        String exportPath, String protocol, List<KeyValueInput> headers) {
        List<McpEndpointInfo> result = new LinkedList<>();
        for (Instance each : instances) {
            McpEndpointInfo endpointInfo = new McpEndpointInfo();
            endpointInfo.setAddress(each.getIp());
            endpointInfo.setPort(each.getPort());
            endpointInfo.setProtocol(protocol);
            endpointInfo.setHeaders(headers);
            endpointInfo.setPath(exportPath);
            result.add(endpointInfo);
        }
        return result;
    }
    
    private List<McpEndpointInfo> transferToMcpEndpointInfo(List<Instance> instances,
        String exportPath, String protocol) {
        return transferToMcpEndpointInfoWithHeaders(instances, exportPath, protocol, null);
    }
    
    private void injectFrontendEndpointRef(McpServerDetailInfo detailInfo) throws NacosException {
        if (detailInfo.getRemoteServerConfig() == null) {
            detailInfo.setFrontendEndpoints(Collections.emptyList());
            return;
        }
        List<FrontEndpointConfig> configs =
            detailInfo.getRemoteServerConfig().getFrontEndpointConfigList();
        if (CollectionUtils.isEmpty(configs)) {
            detailInfo.setFrontendEndpoints(Collections.emptyList());
            return;
        }
        List<McpEndpointInfo> result = new LinkedList<>();
        for (FrontEndpointConfig each : configs) {
            if (AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF.equals(each.getEndpointType())) {
                addReferencedFrontendEndpoints(each, result);
            } else if (AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT.equals(each.getEndpointType())) {
                result.add(toDirectFrontendEndpoint(each));
            } else if (AiConstants.Mcp.MCP_FRONT_ENDPOINT_TYPE_TO_BACK
                .equals(each.getEndpointType())) {
                addBackendFrontendEndpoints(detailInfo, each, result);
            }
        }
        detailInfo.setFrontendEndpoints(result);
    }
    
    private void addReferencedFrontendEndpoints(FrontEndpointConfig config,
        List<McpEndpointInfo> result) throws NacosException {
        McpServiceRef serviceRef = McpRequestUtil.transferToMcpServiceRef(config.getEndpointData());
        List<Instance> instances = getMcpServerEndpointInstances(serviceRef);
        result.addAll(transferToMcpEndpointInfoWithHeaders(instances, config.getPath(),
            config.getProtocol(), config.getHeaders()));
    }
    
    private McpEndpointInfo toDirectFrontendEndpoint(FrontEndpointConfig config) {
        McpEndpointInfo result = new McpEndpointInfo();
        result.setPath(config.getPath());
        result.setProtocol(config.getProtocol());
        result.setHeaders(config.getHeaders());
        String address = String.valueOf(config.getEndpointData());
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
        FrontEndpointConfig config, List<McpEndpointInfo> result) {
        List<McpEndpointInfo> backendEndpoints = detailInfo.getBackendEndpoints();
        backendEndpoints.stream().map(endpoint -> {
            McpEndpointInfo frontend = new McpEndpointInfo();
            frontend.setAddress(endpoint.getAddress());
            frontend.setPort(endpoint.getPort());
            frontend.setProtocol(endpoint.getProtocol());
            frontend.setPath(config.getPath());
            frontend.setHeaders(config.getHeaders());
            return frontend;
        }).forEach(result::add);
    }
    
    /**
     * Delete Mcp Server Endpoint Service.
     *
     * <p>If service not exist, return directly</p>
     * <p>If service exist and service is ref, return directly</p>
     * <p>If service exist and service is direct, do deregister instance and remove service</p>
     *
     * @param namespaceId namespace id of mcp server
     * @param mcpServerName     name of mcp server
     * @throws NacosException any exception during handling
     */
    public void deleteMcpServerEndpointService(String namespaceId, String mcpServerName)
        throws NacosException {
        Service service =
            Service.newService(namespaceId, Constants.MCP_SERVER_ENDPOINT_GROUP, mcpServerName);
        if (isNotExist(service) || !isMcpDirectService(service)) {
            return;
        }
        List<Instance> deletingInstance = instanceOperator.listInstance(namespaceId,
            Constants.MCP_SERVER_ENDPOINT_GROUP, mcpServerName, null, "", false).getHosts();
        for (Instance each : deletingInstance) {
            instanceOperator.removeInstance(namespaceId, Constants.MCP_SERVER_ENDPOINT_GROUP,
                mcpServerName, each);
        }
        serviceOperator.delete(service.getNamespace(), service.getGroupedServiceName());
    }
    
    private boolean isNotExist(Service service) throws NacosException {
        return !ServiceManager.getInstance().containSingleton(service);
    }
    
    private boolean isMcpDirectService(Service service) {
        ServiceMetadata metadata =
            metadataManager.getServiceMetadata(service).orElse(new ServiceMetadata());
        return metadata.getExtendData().containsKey(Constants.MCP_SERVER_ENDPOINT_METADATA_MARK);
    }
    
    private void doCreateNewService(Service service) throws NacosException {
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        clusterMetadata.setHealthyCheckType(AbstractHealthChecker.None.TYPE);
        clusterMetadata.setHealthChecker(new AbstractHealthChecker.None());
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.getClusters().put(Constants.MCP_SERVER_ENDPOINT_CLUSTER, clusterMetadata);
        serviceMetadata.setEphemeral(false);
        // Mark service as direct service
        serviceMetadata.getExtendData().put(Constants.MCP_SERVER_ENDPOINT_METADATA_MARK, "true");
        serviceOperator.create(service.getNamespace(), service.getGroupedServiceName(),
            serviceMetadata);
    }
    
    private void doUpdateInstanceInfo(Service service, McpEndpointSpec endpointSpecification,
        String namespaceId, String mcpServerName, boolean overrideExisting, String versionMcpName)
        throws NacosException {
        Instance instance = new Instance();
        instance.setIp(endpointSpecification.getData().get(Constants.MCP_SERVER_ENDPOINT_ADDRESS));
        instance.setPort(Integer
            .parseInt(endpointSpecification.getData().get(Constants.MCP_SERVER_ENDPOINT_PORT)));
        instance.setClusterName(Constants.MCP_SERVER_ENDPOINT_CLUSTER);
        instance.setEphemeral(false);
        if (overrideExisting) {
            List<Instance> oldInstances = instanceOperator.listInstance(namespaceId,
                Constants.MCP_SERVER_ENDPOINT_GROUP, versionMcpName, null, "", false).getHosts();
            for (Instance each : oldInstances) {
                instanceOperator.removeInstance(namespaceId, Constants.MCP_SERVER_ENDPOINT_GROUP,
                    versionMcpName, each);
            }
        }
        instanceOperator.registerInstance(service.getNamespace(), service.getGroup(),
            service.getName(), instance);
    }
}
