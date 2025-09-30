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
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.naming.core.InstanceOperator;
import com.alibaba.nacos.naming.core.ServiceOperator;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.List;
import java.util.Map;

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
    
    public McpEndpointOperationService(ServiceOperator serviceOperator, InstanceOperator instanceOperator,
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
    public Service createMcpServerEndpointServiceIfNecessary(String namespaceId, String mcpName, String version,
            McpEndpointSpec endpointSpecification, boolean overrideExisting) throws NacosException {
        if (AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF.equalsIgnoreCase(endpointSpecification.getType())) {
            Map<String, String> endpointServiceData = endpointSpecification.getData();
            if (!endpointServiceData.containsKey(CommonParams.NAMESPACE_ID) || !endpointServiceData.containsKey(
                    CommonParams.GROUP_NAME) || !endpointServiceData.containsKey(CommonParams.SERVICE_NAME)) {
                throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
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
            doUpdateInstanceInfo(service, endpointSpecification, namespaceId, mcpName, overrideExisting, versionMcpName);
            return service;
        }
        doUpdateInstanceInfo(service, endpointSpecification, namespaceId, mcpName, overrideExisting, versionMcpName);
        return service;
    }
    
    public Service generateService(String namespaceId, String mcpName) {
        return Service.newService(namespaceId, Constants.MCP_SERVER_ENDPOINT_GROUP, mcpName);
    }
    
    public List<Instance> getMcpServerEndpointInstances(McpServiceRef serviceRef) throws NacosException {
        return instanceOperator.listInstance(serviceRef.getNamespaceId(), serviceRef.getGroupName(),
                serviceRef.getServiceName(), null, "", true).getHosts();
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
    public void deleteMcpServerEndpointService(String namespaceId, String mcpServerName) throws NacosException {
        Service service = Service.newService(namespaceId, Constants.MCP_SERVER_ENDPOINT_GROUP, mcpServerName);
        if (isNotExist(service) || !isMcpDirectService(service)) {
            return;
        }
        List<Instance> deletingInstance = instanceOperator.listInstance(namespaceId,
                Constants.MCP_SERVER_ENDPOINT_GROUP, mcpServerName, null, "", false).getHosts();
        for (Instance each : deletingInstance) {
            instanceOperator.removeInstance(namespaceId, Constants.MCP_SERVER_ENDPOINT_GROUP, mcpServerName, each);
        }
        serviceOperator.delete(service.getNamespace(), service.getGroupedServiceName());
    }
    
    private boolean isNotExist(Service service) throws NacosException {
        return !ServiceManager.getInstance().containSingleton(service);
    }
    
    private boolean isMcpDirectService(Service service) {
        ServiceMetadata metadata = metadataManager.getServiceMetadata(service).orElse(new ServiceMetadata());
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
        serviceOperator.create(service.getNamespace(), service.getGroupedServiceName(), serviceMetadata);
    }
    
    private void doUpdateInstanceInfo(Service service, McpEndpointSpec endpointSpecification,
                          String namespaceId, String mcpServerName, boolean overrideExisting, String versionMcpName) throws NacosException {
        Instance instance = new Instance();
        instance.setIp(endpointSpecification.getData().get(Constants.MCP_SERVER_ENDPOINT_ADDRESS));
        instance.setPort(Integer.parseInt(endpointSpecification.getData().get(Constants.MCP_SERVER_ENDPOINT_PORT)));
        instance.setClusterName(Constants.MCP_SERVER_ENDPOINT_CLUSTER);
        instance.setEphemeral(false);
        if (overrideExisting) {
            List<Instance> oldInstances = instanceOperator.listInstance(namespaceId,
                    Constants.MCP_SERVER_ENDPOINT_GROUP, versionMcpName, null, "", false).getHosts();
            for (Instance each : oldInstances) {
                instanceOperator.removeInstance(namespaceId, Constants.MCP_SERVER_ENDPOINT_GROUP, versionMcpName, each);
            }
        }
        instanceOperator.registerInstance(service.getNamespace(), service.getGroup(), service.getName(), instance);
    }
}
