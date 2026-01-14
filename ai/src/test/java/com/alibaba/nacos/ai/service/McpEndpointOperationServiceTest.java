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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.core.InstanceOperator;
import com.alibaba.nacos.naming.core.ServiceOperator;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpEndpointOperationServiceTest {
    
    @Mock
    private ServiceOperator serviceOperator;
    
    @Mock
    private InstanceOperator instanceOperator;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    McpEndpointOperationService endpointOperationService;
    
    @BeforeEach
    void setUp() {
        endpointOperationService = new McpEndpointOperationService(serviceOperator, instanceOperator, metadataManager);
    }
    
    @AfterEach
    void tearDown() {
        Service service = Service.newService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, Constants.MCP_SERVER_ENDPOINT_GROUP,
                "mcpName::1.0.0");
        ServiceManager.getInstance().removeSingleton(service);
    }
    
    @Test
    void createMcpServerEndpointServiceIfNecessaryTypeRefWithoutMsg() {
        McpEndpointSpec mcpEndpointSpec = new McpEndpointSpec();
        mcpEndpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
        assertThrows(NacosApiException.class, () -> endpointOperationService.createMcpServerEndpointServiceIfNecessary(
                        AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName", "1.0.0", mcpEndpointSpec, false),
                "`namespaceId`, `groupName`, `serviceName` should be in remoteServerConfig data if type is `REF`");
        mcpEndpointSpec.getData().put("namespaceId", AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        assertThrows(NacosApiException.class, () -> endpointOperationService.createMcpServerEndpointServiceIfNecessary(
                        AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName", "1.0.0", mcpEndpointSpec, false),
                "`namespaceId`, `groupName`, `serviceName` should be in remoteServerConfig data if type is `REF`");
        mcpEndpointSpec.getData().put("groupName", "groupName");
        assertThrows(NacosApiException.class, () -> endpointOperationService.createMcpServerEndpointServiceIfNecessary(
                        AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName", "1.0.0", mcpEndpointSpec, false),
                "`namespaceId`, `groupName`, `serviceName` should be in remoteServerConfig data if type is `REF`");
    }
    
    @Test
    void createMcpServerEndpointServiceIfNecessaryTypeRef() throws NacosException {
        McpEndpointSpec mcpEndpointSpec = new McpEndpointSpec();
        mcpEndpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
        mcpEndpointSpec.getData().put("namespaceId", AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        mcpEndpointSpec.getData().put("groupName", "groupName");
        mcpEndpointSpec.getData().put("serviceName", "serviceName");
        Service service = endpointOperationService.createMcpServerEndpointServiceIfNecessary(
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName", "1.0.0", mcpEndpointSpec, false);
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, service.getNamespace());
        assertEquals("groupName", service.getGroup());
        assertEquals("serviceName", service.getName());
    }
    
    @Test
    void createMcpServerEndpointServiceIfNecessaryTypeDirectWithoutExistService() throws NacosException {
        McpEndpointSpec mcpEndpointSpec = new McpEndpointSpec();
        mcpEndpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        mcpEndpointSpec.getData().put("address", "127.0.0.1");
        mcpEndpointSpec.getData().put("port", "8848");
        Service service = endpointOperationService.createMcpServerEndpointServiceIfNecessary(
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName", "1.0.0", mcpEndpointSpec, false);
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, service.getNamespace());
        assertEquals(Constants.MCP_SERVER_ENDPOINT_GROUP, service.getGroup());
        assertEquals("mcpName::1.0.0", service.getName());
        verify(serviceOperator).create(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                eq(NamingUtils.getGroupedName("mcpName::1.0.0", Constants.MCP_SERVER_ENDPOINT_GROUP)),
                any(ServiceMetadata.class));
        verify(instanceOperator).registerInstance(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                eq(Constants.MCP_SERVER_ENDPOINT_GROUP), eq("mcpName::1.0.0"), any(Instance.class));
    }
    
    @Test
    void createMcpServerEndpointServiceIfNecessaryTypeDirectWithExistService() throws NacosException {
        ServiceManager.getInstance().getSingleton(
                Service.newService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, Constants.MCP_SERVER_ENDPOINT_GROUP,
                        "mcpName::1.0.0"));
        McpEndpointSpec mcpEndpointSpec = new McpEndpointSpec();
        mcpEndpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        mcpEndpointSpec.getData().put("address", "127.0.0.1");
        mcpEndpointSpec.getData().put("port", "8848");
        Service service = endpointOperationService.createMcpServerEndpointServiceIfNecessary(
                AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName", "1.0.0", mcpEndpointSpec, false);
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, service.getNamespace());
        assertEquals(Constants.MCP_SERVER_ENDPOINT_GROUP, service.getGroup());
        assertEquals("mcpName::1.0.0", service.getName());
        verify(serviceOperator, never()).create(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                eq(NamingUtils.getGroupedName("mcpName::1.0.0", Constants.MCP_SERVER_ENDPOINT_GROUP)),
                any(ServiceMetadata.class));
        verify(instanceOperator).registerInstance(eq(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE),
                eq(Constants.MCP_SERVER_ENDPOINT_GROUP), eq("mcpName::1.0.0"), any(Instance.class));
    }
    
    @Test
    void getMcpServerEndpointInstances() throws NacosException {
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8848);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.singletonList(instance));
        when(instanceOperator.listInstance(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, Constants.MCP_SERVER_ENDPOINT_GROUP,
                "mcpName", null, "", true)).thenReturn(serviceInfo);
        McpServiceRef serviceRef = new McpServiceRef();
        serviceRef.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        serviceRef.setGroupName(Constants.MCP_SERVER_ENDPOINT_GROUP);
        serviceRef.setServiceName("mcpName");
        List<Instance> actual = endpointOperationService.getMcpServerEndpointInstances(serviceRef);
        assertEquals(1, actual.size());
        assertEquals("127.0.0.1", actual.get(0).getIp());
        assertEquals(8848, actual.get(0).getPort());
    }
    
    @Test
    void deleteMcpServerEndpointServiceForNonExistService() throws NacosException {
        endpointOperationService.deleteMcpServerEndpointService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName");
        verify(instanceOperator, never()).removeInstance(anyString(), anyString(), anyString(), any(Instance.class));
        verify(serviceOperator, never()).delete(anyString(), anyString());
    }
    
    @Test
    void deleteMcpServerEndpointServiceForRefService() throws NacosException {
        Service service = Service.newService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, Constants.MCP_SERVER_ENDPOINT_GROUP,
                "mcpName");
        ServiceManager.getInstance().getSingleton(service);
        when(metadataManager.getServiceMetadata(service)).thenReturn(Optional.empty());
        endpointOperationService.deleteMcpServerEndpointService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName");
        verify(instanceOperator, never()).removeInstance(anyString(), anyString(), anyString(), any(Instance.class));
        verify(serviceOperator, never()).delete(anyString(), anyString());
    }
    
    @Test
    void deleteMcpServerEndpointService() throws NacosException {
        Service service = Service.newService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, Constants.MCP_SERVER_ENDPOINT_GROUP,
                "mcpName");
        ServiceManager.getInstance().getSingleton(service);
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.getExtendData()
                .put(Constants.MCP_SERVER_ENDPOINT_METADATA_MARK, Constants.MCP_SERVER_ENDPOINT_METADATA_MARK);
        when(metadataManager.getServiceMetadata(service)).thenReturn(Optional.of(serviceMetadata));
        List<Instance> instances = new LinkedList<>();
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8848);
        instances.add(instance);
        instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(9848);
        instances.add(instance);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(instances);
        when(instanceOperator.listInstance(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, Constants.MCP_SERVER_ENDPOINT_GROUP,
                "mcpName", null, "", false)).thenReturn(serviceInfo);
        endpointOperationService.deleteMcpServerEndpointService(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "mcpName");
        for (Instance each : instances) {
            verify(instanceOperator).removeInstance(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                    Constants.MCP_SERVER_ENDPOINT_GROUP, "mcpName", each);
        }
        verify(serviceOperator).delete(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                NamingUtils.getGroupedName("mcpName", Constants.MCP_SERVER_ENDPOINT_GROUP));
    }
}