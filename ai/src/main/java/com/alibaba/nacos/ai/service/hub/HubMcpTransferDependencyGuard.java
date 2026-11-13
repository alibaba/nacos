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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.naming.core.InstanceOperator;
import com.alibaba.nacos.naming.core.ServiceOperator;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import java.util.List;
import java.util.Objects;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

/** Prepare MCP-owned Direct state or retain references selected by a pinned Hub publication. */
@org.springframework.stereotype.Service
public class HubMcpTransferDependencyGuard {
    
    static final String IMPORT_OWNER = "__nacos.ai.mcp.hub.import__";
    
    private final ServiceOperator services;
    private final InstanceOperator instances;
    private final NamingMetadataManager metadata;
    
    public HubMcpTransferDependencyGuard(ServiceOperator services, InstanceOperator instances,
        NamingMetadataManager metadata) {
        this.services = services;
        this.instances = instances;
        this.metadata = metadata;
    }
    
    /** Read the pinned source and prepare a target plan without writing Naming or storage. */
    public PreparedImport prepare(String sourceNamespace, String sourceName, String sourceVersion,
        McpServerBasicInfo server, String targetNamespace, String targetName, String targetVersion,
        String importOwner, McpServerRemoteServiceConfig override) throws NacosException {
        McpServerRemoteServiceConfig configuration =
            select(server.getRemoteServerConfig(), override);
        if (AiConstants.Mcp.MCP_PROTOCOL_STDIO.equalsIgnoreCase(server.getProtocol())) {
            return new PreparedImport(configuration, null, null, null, importOwner);
        }
        McpServiceRef ref = configuration == null ? null : configuration.getServiceRef();
        Service source = ref == null ? null
            : Service.newService(ref.getNamespaceId(),
                ref.getGroupName(), ref.getServiceName());
        boolean owned = ref != null && sourceNamespace.equals(ref.getNamespaceId())
            && Constants.MCP_SERVER_ENDPOINT_GROUP.equals(ref.getGroupName())
            && (sourceName + "::" + sourceVersion).equals(ref.getServiceName())
            && isDirect(source);
        if (!owned) {
            return new PreparedImport(configuration, null, null, null, importOwner);
        }
        FixedAddress address = fixedAddress(source);
        Service target =
            Service.newService(targetNamespace,
                Constants.MCP_SERVER_ENDPOINT_GROUP, targetName + "::" + targetVersion, false);
        requireAbsent(target);
        ref.setNamespaceId(targetNamespace);
        ref.setGroupName(target.getGroup());
        ref.setServiceName(target.getName());
        return new PreparedImport(configuration, source, target, address, importOwner);
    }
    
    private McpServerRemoteServiceConfig select(
        McpServerRemoteServiceConfig source, McpServerRemoteServiceConfig override)
        throws NacosException {
        if (override != null && !java.util.Objects.equals(JacksonUtils.toJson(source),
            JacksonUtils.toJson(override))) {
            throw new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Hub import derives MCP connections from the pinned source");
        }
        if (source == null) {
            return null;
        }
        McpServerRemoteServiceConfig result = JacksonUtils.toObj(JacksonUtils.toJson(source),
            McpServerRemoteServiceConfig.class);
        validateReferences(result);
        return result;
    }
    
    private void validateReferences(McpServerRemoteServiceConfig configuration)
        throws NacosException {
        if (configuration == null) {
            return;
        }
        if (configuration.getServiceRef() != null) {
            requireReference(configuration.getServiceRef());
        }
        if (configuration.getFrontEndpointConfigList() != null) {
            for (FrontEndpointConfig frontend : configuration.getFrontEndpointConfigList()) {
                if (frontend != null && "REF".equals(frontend.getEndpointType())) {
                    requireReference(
                        McpRequestUtil.transferToMcpServiceRef(frontend.getEndpointData()));
                }
            }
        }
    }
    
    private boolean isDirect(Service service) {
        return metadata.getServiceMetadata(service)
            .map(value -> "true".equals(String.valueOf(value.getExtendData()
                .get(Constants.MCP_SERVER_ENDPOINT_METADATA_MARK))))
            .orElse(false);
    }
    
    private FixedAddress fixedAddress(Service service)
        throws NacosException {
        List<Instance> fixed = instances.listInstance(service.getNamespace(), service.getGroup(),
            service.getName(), null, "", false).getHosts().stream()
            .filter(value -> !value.isEphemeral()
                && Constants.MCP_SERVER_ENDPOINT_CLUSTER.equals(value.getClusterName()))
            .toList();
        if (fixed.size() != 1 || StringUtils.isBlank(fixed.get(0).getIp())
            || fixed.get(0).getPort() < 1 || fixed.get(0).getPort() > 65535) {
            throw new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT,
                "Source MCP Direct service must have one unambiguous persistent fixed address");
        }
        return new FixedAddress(fixed.get(0).getIp(), fixed.get(0).getPort());
    }
    
    private void requireAbsent(Service service)
        throws NacosException {
        if (ServiceManager.getInstance().containSingleton(service)
            || metadata.getServiceMetadata(service).isPresent()) {
            throw new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT,
                "Target MCP Naming service already exists");
        }
    }
    
    private record FixedAddress(String address, int port) {
        
        Instance instance() {
            Instance result = new Instance();
            result.setIp(address);
            result.setPort(port);
            result.setEphemeral(false);
            result.setClusterName(Constants.MCP_SERVER_ENDPOINT_CLUSTER);
            return result;
        }
    }
    
    /** Target Naming state participates in the same reserved draft write and rollback as content. */
    public final class PreparedImport {
        
        private final McpServerRemoteServiceConfig configuration;
        private final Service source;
        private final Service target;
        private final FixedAddress address;
        private final String owner;
        private boolean attempted;
        
        private PreparedImport(McpServerRemoteServiceConfig configuration,
            Service source,
            Service target, FixedAddress address, String owner) {
            this.configuration = configuration;
            this.source = source;
            this.target = target;
            this.address = address;
            this.owner = owner;
        }
        
        /** Return an independent copy of the prepared target connection configuration. */
        public McpServerRemoteServiceConfig configuration() {
            return configuration == null ? null
                : JacksonUtils.toObj(JacksonUtils.toJson(configuration),
                    McpServerRemoteServiceConfig.class);
        }
        
        /** Recheck fixed source state and target ownership around draft activation. */
        public void revalidate() throws NacosException {
            validateReferences(configuration);
            if (target != null && (!isDirect(source) || !address.equals(fixedAddress(source))
                || !ownedTarget() || !address.equals(fixedAddress(target)))) {
                throw new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT,
                    "MCP Direct dependency changed during Hub import");
            }
        }
        
        /** Compose target Naming materialization with independently stored resource content. */
        public HubResourceDraftTransferWriter.PreparedContent attach(
            HubResourceDraftTransferWriter.PreparedContent content) {
            if (target == null) {
                return content;
            }
            return new HubResourceDraftTransferWriter.PreparedContent() {
                
                @Override
                public String write() throws NacosException {
                    createDirect();
                    return content.write();
                }
                
                @Override
                public void cleanup() throws NacosException {
                    // Keep the name reserved if content cleanup fails; never abandon its service.
                    content.cleanup();
                    cleanupDirect();
                }
            };
        }
        
        private boolean ownedTarget() {
            return metadata.getServiceMetadata(target)
                .map(value -> Objects.equals(owner, value.getExtendData().get(IMPORT_OWNER))
                    && "true".equals(String.valueOf(value.getExtendData()
                        .get(Constants.MCP_SERVER_ENDPOINT_METADATA_MARK))))
                .orElse(false);
        }
        
        private void createDirect() throws NacosException {
            if (!isDirect(source) || !address.equals(fixedAddress(source))) {
                throw new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT,
                    "Source MCP Direct address changed before import");
            }
            requireAbsent(target);
            ServiceMetadata serviceMetadata = new ServiceMetadata();
            ClusterMetadata cluster = new ClusterMetadata();
            cluster.setHealthyCheckType(AbstractHealthChecker.None.TYPE);
            cluster.setHealthChecker(new AbstractHealthChecker.None());
            serviceMetadata.getClusters().put(Constants.MCP_SERVER_ENDPOINT_CLUSTER, cluster);
            serviceMetadata.setEphemeral(false);
            serviceMetadata.getExtendData().put(Constants.MCP_SERVER_ENDPOINT_METADATA_MARK,
                "true");
            serviceMetadata.getExtendData().put(IMPORT_OWNER, owner);
            attempted = true;
            services.create(target.getNamespace(), target.getGroupedServiceName(), serviceMetadata);
            instances.registerInstance(target.getNamespace(), target.getGroup(), target.getName(),
                address.instance());
        }
        
        private void cleanupDirect() throws NacosException {
            if (!attempted) {
                return;
            }
            if (!ownedTarget()) {
                if (ServiceManager.getInstance().containSingleton(target)
                    || metadata.getServiceMetadata(target).isPresent()) {
                    throw new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT,
                        "Target MCP Naming ownership changed; cleanup refused");
                }
                return;
            }
            List<Instance> current =
                instances.listInstance(target.getNamespace(), target.getGroup(),
                    target.getName(), null, "", false).getHosts();
            for (Instance instance : current) {
                if (instance.isEphemeral() || !Objects.equals(address.address(), instance.getIp())
                    || address.port() != instance.getPort()
                    || !Constants.MCP_SERVER_ENDPOINT_CLUSTER.equals(instance.getClusterName())) {
                    throw new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT,
                        "Target MCP instances changed; cleanup refused");
                }
            }
            for (Instance instance : current) {
                instances.removeInstance(target.getNamespace(), target.getGroup(), target.getName(),
                    instance);
            }
            services.delete(target.getNamespace(), target.getGroupedServiceName());
        }
    }
    
    private void requireReference(McpServiceRef reference) throws NacosException {
        if (reference == null || StringUtils.isBlank(reference.getNamespaceId())
            || StringUtils.isBlank(reference.getGroupName())
            || StringUtils.isBlank(reference.getServiceName())) {
            throw new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Invalid source MCP service reference");
        }
    }
}
