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
import com.alibaba.nacos.api.ai.model.mcp.FrontEndpointConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.InstanceOperator;
import com.alibaba.nacos.naming.core.ServiceOperator;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Real Naming materialization, fixed-address selection, create-only behavior and owned rollback. */
class HubMcpTransferDependencyGuardTest {
    
    private final ServiceOperator services = mock(ServiceOperator.class);
    private final InstanceOperator instances = mock(InstanceOperator.class);
    private final NamingMetadataManager metadata = mock(NamingMetadataManager.class);
    private final HubMcpTransferDependencyGuard guard =
        new HubMcpTransferDependencyGuard(services, instances, metadata);
    private final Map<Service, ServiceMetadata> metadataState = new HashMap<>();
    private final Map<Service, List<Instance>> instanceState = new HashMap<>();
    private final Service source =
        Service.newService("source-ns", "mcp-endpoints", "source::1.0.0");
    private final Service target =
        Service.newService("target-ns", "mcp-endpoints", "target::0.0.1");
    private final McpServerBasicInfo server = new McpServerBasicInfo();
    private final HubResourceDraftTransferWriter.PreparedContent files =
        mock(HubResourceDraftTransferWriter.PreparedContent.class);
    
    @BeforeEach
    void setUp() throws Exception {
        server.setProtocol("mcp-streamable");
        server.setRemoteServerConfig(reference("source-ns", "mcp-endpoints", "source::1.0.0"));
        ServiceMetadata marker = new ServiceMetadata();
        marker.getExtendData().put(Constants.MCP_SERVER_ENDPOINT_METADATA_MARK, "true");
        marker.setEphemeral(false);
        metadataState.put(source, marker);
        Instance fixed = instance("10.0.0.1", false);
        fixed.setHealthy(false);
        fixed.setEnabled(false);
        instanceState.put(source, List.of(fixed));
        when(metadata.getServiceMetadata(any())).thenAnswer(
            call -> Optional.ofNullable(metadataState.get(call.<Service>getArgument(0))));
        when(instances.listInstance(anyString(), anyString(), anyString(), any(), anyString(),
            eq(false)))
            .thenAnswer(call -> {
                Service key = Service.newService(call.getArgument(0), call.getArgument(1),
                    call.getArgument(2));
                ServiceInfo result = new ServiceInfo();
                result.setHosts(instanceState.getOrDefault(key, List.of()));
                return result;
            });
        doAnswer(call -> {
            String[] grouped = call.<String>getArgument(1).split("@@", 2);
            metadataState.put(Service.newService(call.getArgument(0), grouped[0], grouped[1]),
                call.getArgument(2));
            return null;
        }).when(services).create(anyString(), anyString(), any());
        doAnswer(call -> {
            Service key =
                Service.newService(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            instanceState.put(key, new ArrayList<>(List.of(call.<Instance>getArgument(3))));
            return null;
        }).when(instances).registerInstance(anyString(), anyString(), anyString(), any());
        when(files.write()).thenReturn("target-storage");
    }
    
    @Test
    void directCreatesIndependentServiceAndPreservesAllFrontendDefinitions() throws Exception {
        FrontEndpointConfig backend = new FrontEndpointConfig();
        backend.setEndpointType("BACKEND");
        backend.setPath("/backend");
        FrontEndpointConfig ref = new FrontEndpointConfig();
        ref.setEndpointType("REF");
        ref.setEndpointData(
            Map.of("namespaceId", "frontend-ns", "groupName", "gateway", "serviceName", "front"));
        FrontEndpointConfig direct = new FrontEndpointConfig();
        direct.setEndpointType("DIRECT");
        direct.setEndpointData("gateway.example:443");
        server.getRemoteServerConfig().setFrontEndpointConfigList(List.of(backend, ref, direct));
        final String original = JacksonUtils.toJson(server);
        HubMcpTransferDependencyGuard.PreparedImport plan = prepare();
        verifyNoInteractions(services);
        McpServerRemoteServiceConfig remote = plan.configuration();
        assertNotSame(server.getRemoteServerConfig(), remote);
        assertEquals("target-ns", remote.getServiceRef().getNamespaceId());
        assertEquals("target::0.0.1", remote.getServiceRef().getServiceName());
        assertEquals(
            JacksonUtils.toJson(server.getRemoteServerConfig().getFrontEndpointConfigList()),
            JacksonUtils.toJson(remote.getFrontEndpointConfigList()));
        assertEquals("/mcp", remote.getExportPath());
        assertEquals("https", remote.getServiceRef().getTransportProtocol());
        assertEquals("target-storage", plan.attach(files).write());
        plan.revalidate();
        ArgumentCaptor<Instance> created = ArgumentCaptor.forClass(Instance.class);
        verify(instances).registerInstance(eq("target-ns"), eq("mcp-endpoints"),
            eq("target::0.0.1"), created.capture());
        assertEquals("10.0.0.1", created.getValue().getIp());
        assertEquals(8080, created.getValue().getPort());
        assertFalse(created.getValue().isEphemeral());
        assertEquals("owner-token", metadataState.get(target).getExtendData()
            .get(HubMcpTransferDependencyGuard.IMPORT_OWNER));
        assertEquals(original, JacksonUtils.toJson(server));
    }
    
    @Test
    void refKeepsOriginalServiceEvenWithVersionLikeNameWithoutOwnership() throws Exception {
        metadataState.clear();
        HubMcpTransferDependencyGuard.PreparedImport plan = prepare();
        assertEquals("source-ns", plan.configuration().getServiceRef().getNamespaceId());
        plan.attach(files).write();
        plan.revalidate();
        verifyNoInteractions(services, instances);
    }
    
    @Test
    void foreignNamespaceIsRefEvenWithDirectMarkerAndMatchingName() throws Exception {
        server.getRemoteServerConfig().getServiceRef().setNamespaceId("foreign-ns");
        assertEquals("foreign-ns", prepare().configuration().getServiceRef().getNamespaceId());
        verifyNoInteractions(services, instances);
    }
    
    @Test
    void stdioCopiesDefinitionsWithoutNamingWrites() throws Exception {
        server.setProtocol("stdio");
        server.setRemoteServerConfig(null);
        HubMcpTransferDependencyGuard.PreparedImport plan = prepare();
        assertNull(plan.configuration());
        plan.attach(files).write();
        plan.revalidate();
        verifyNoInteractions(services, instances, metadata);
    }
    
    @Test
    void dynamicInstancesAreIgnoredInsteadOfTakingFirstEndpoint() throws Exception {
        instanceState.put(source,
            List.of(instance("10.0.0.99", true), instance("10.0.0.1", false)));
        prepare().attach(files).write();
        ArgumentCaptor<Instance> created = ArgumentCaptor.forClass(Instance.class);
        verify(instances).registerInstance(anyString(), anyString(), anyString(),
            created.capture());
        assertEquals("10.0.0.1", created.getValue().getIp());
    }
    
    @Test
    void missingAmbiguousAndInvalidPersistentAddressFailBeforeWrites() throws Exception {
        for (List<Instance> values : List.of(List.<Instance>of(),
            List.of(instance("10.0.0.1", true)),
            List.of(instance("10.0.0.1", false), instance("10.0.0.2", false)),
            List.of(instance("", false)))) {
            instanceState.put(source, values);
            assertEquals(409, assertThrows(NacosException.class, this::prepare).getErrCode());
        }
        verifyNoInteractions(services, files);
    }
    
    @Test
    void orphanTargetServiceIsConflictAndNeverReceivesInstances() throws Exception {
        metadataState.put(target, new ServiceMetadata());
        assertEquals(409, assertThrows(NacosException.class, this::prepare).getErrCode());
        verifyNoInteractions(services, files);
        verify(instances, never()).registerInstance(anyString(), anyString(), anyString(), any());
    }
    
    @Test
    void targetAppearingAfterPreparationIsNeverModifiedOrDeleted() throws Exception {
        HubResourceDraftTransferWriter.PreparedContent content = prepare().attach(files);
        metadataState.put(target, new ServiceMetadata());
        assertThrows(NacosException.class, content::write);
        content.cleanup();
        verifyNoInteractions(services);
        verify(files, never()).write();
    }
    
    @Test
    void sourceAddressChangeBeforeWriteFailsWithoutCreatingTarget() throws Exception {
        HubResourceDraftTransferWriter.PreparedContent content = prepare().attach(files);
        instanceState.put(source, List.of(instance("10.0.0.2", false)));
        assertThrows(NacosException.class, content::write);
        content.cleanup();
        verifyNoInteractions(services);
        verify(files, never()).write();
    }
    
    @Test
    void failedContentWriteRemovesOnlyOwnedTargetService() throws Exception {
        HubResourceDraftTransferWriter.PreparedContent content = prepare().attach(files);
        doThrow(new NacosException(500, "partial content write")).when(files).write();
        assertThrows(NacosException.class, content::write);
        content.cleanup();
        verify(files).cleanup();
        verify(instances).removeInstance(eq("target-ns"), eq("mcp-endpoints"), eq("target::0.0.1"),
            any());
        verify(services).delete("target-ns", "mcp-endpoints@@target::0.0.1");
        verify(services, never()).delete("source-ns", "mcp-endpoints@@source::1.0.0");
    }
    
    @Test
    void failedInstanceRegistrationStillCleansCreatedService() throws Exception {
        HubResourceDraftTransferWriter.PreparedContent content = prepare().attach(files);
        doThrow(new NacosException(500, "register failed")).when(instances)
            .registerInstance(anyString(), anyString(), anyString(), any());
        assertThrows(NacosException.class, content::write);
        content.cleanup();
        verify(services).delete("target-ns", "mcp-endpoints@@target::0.0.1");
        verify(files, never()).write();
    }
    
    @Test
    void ownershipChangePreventsRollbackDeletingAnotherService() throws Exception {
        HubResourceDraftTransferWriter.PreparedContent content = prepare().attach(files);
        content.write();
        metadataState.get(target).getExtendData().put(HubMcpTransferDependencyGuard.IMPORT_OWNER,
            "someone-else");
        assertThrows(NacosException.class, content::cleanup);
        verify(services, never()).delete(anyString(), anyString());
        verify(instances, never()).removeInstance(anyString(), anyString(), anyString(), any());
    }
    
    @Test
    void unexpectedTargetPublisherPreventsRollbackRemovingDynamicState() throws Exception {
        HubResourceDraftTransferWriter.PreparedContent content = prepare().attach(files);
        content.write();
        instanceState.get(target).add(instance("10.0.0.99", true));
        assertThrows(NacosException.class, content::cleanup);
        verify(services, never()).delete(anyString(), anyString());
        verify(instances, never()).removeInstance(anyString(), anyString(), anyString(), any());
    }
    
    @Test
    void sourceChangesAfterWriteAreCaughtAtActivationAndTargetCanBeCleaned() throws Exception {
        HubMcpTransferDependencyGuard.PreparedImport plan = prepare();
        HubResourceDraftTransferWriter.PreparedContent content = plan.attach(files);
        content.write();
        instanceState.put(source, List.of(instance("10.0.0.2", false)));
        assertThrows(NacosException.class, plan::revalidate);
        content.cleanup();
        verify(services).delete("target-ns", "mcp-endpoints@@target::0.0.1");
    }
    
    @Test
    void callerCannotOverridePinnedConnections() {
        assertThrows(NacosException.class,
            () -> guard.prepare("source-ns", "source", "1.0.0", server,
                "target-ns", "target", "0.0.1", "owner-token",
                reference("elsewhere", "group", "service")));
        verifyNoInteractions(services, instances, metadata);
    }
    
    private HubMcpTransferDependencyGuard.PreparedImport prepare() throws NacosException {
        return guard.prepare("source-ns", "source", "1.0.0", server,
            "target-ns", "target", "0.0.1", "owner-token", null);
    }
    
    private Instance instance(String address, boolean ephemeral) {
        Instance result = new Instance();
        result.setIp(address);
        result.setPort(8080);
        result.setClusterName(Constants.MCP_SERVER_ENDPOINT_CLUSTER);
        result.setEphemeral(ephemeral);
        return result;
    }
    
    private McpServerRemoteServiceConfig reference(String namespace, String group, String name) {
        McpServiceRef ref = new McpServiceRef();
        ref.setNamespaceId(namespace);
        ref.setGroupName(group);
        ref.setServiceName(name);
        ref.setTransportProtocol("https");
        McpServerRemoteServiceConfig result = new McpServerRemoteServiceConfig();
        result.setServiceRef(ref);
        result.setExportPath("/mcp");
        return result;
    }
}
