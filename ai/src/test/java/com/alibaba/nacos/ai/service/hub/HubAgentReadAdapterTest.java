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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.agent.AgentPersistenceService;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchRequest;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Agent catalog isolation and exact-version RAD callbacks without Publisher ownership changes. */
class HubAgentReadAdapterTest {
    
    private final HubResourcePublicationService publications =
        mock(HubResourcePublicationService.class);
    private final HubResourceDirectoryService directory = mock(HubResourceDirectoryService.class);
    private final HubResourceDiscoveryProjector projector =
        mock(HubResourceDiscoveryProjector.class);
    private final HubResourceContentService contents = mock(HubResourceContentService.class);
    private final AgentPersistenceService agents = mock(AgentPersistenceService.class);
    private final AiResourceManager manager = mock(AiResourceManager.class);
    private final HubAgentReadAdapter adapter =
        new HubAgentReadAdapter(publications, directory, projector,
            contents, agents, manager);
    private final AiResourceVersion version = new AiResourceVersion();
    
    @BeforeEach
    void setUp() throws Exception {
        HubResourceReference reference =
            HubResourceReferenceJsonUtil.build("source", 101L, "same-name", 201L, "1.0.0");
        AiResource hub = new AiResource();
        hub.setId(501L);
        hub.setName("same-name-101");
        lenient()
            .when(publications.inspectPinned(eq(HubResourceType.AGENT), eq(501L), any(), any()))
            .thenAnswer(call -> {
                HubResourcePublicationService.PinnedPublicationReader<?> reader =
                    call.getArgument(3);
                return reader.read(hub, new AiResource(), reference);
            });
        AgentSummary source = new AgentSummary();
        source.setNamespaceId("source");
        source.setAgentName("same-name");
        source.setDisplayName("Agent display");
        source.setOwner("author");
        source.setMetaVersion(999L);
        AgentVersionSummary first = new AgentVersionSummary();
        first.setVersion("1.0.0");
        first.setProtocols(Collections.singletonList("a2a"));
        AgentVersionSummary other = new AgentVersionSummary();
        other.setVersion("2.0.0");
        other.setProtocols(Collections.singletonList("other"));
        AgentVersionInfo catalog = new AgentVersionInfo();
        catalog.setLabels(Collections.singletonMap("latest", "2.0.0"));
        catalog.setOnlineVersions(Arrays.asList(other, first));
        source.setVersionInfo(catalog);
        lenient().when(agents.getAgent("source", "same-name")).thenReturn(source);
        AgentVersionStorageDescriptor descriptor = new AgentVersionStorageDescriptor();
        descriptor.setProvider(AgentVersionStorageDescriptorSerializer.NACOS_CONFIG_PROVIDER);
        descriptor.setKey("agent-version/demo");
        descriptor.setKeyFormat(AgentVersionStorageDescriptorSerializer.NACOS_CONFIG_KEY_FORMAT);
        descriptor
            .setAgentNameCodec(AgentVersionStorageDescriptorSerializer.RAD_ASCII_AGENT_NAME_CODEC);
        descriptor.setMediaType(AgentVersionStorageDescriptorSerializer.AGENT_VERSION_MEDIA_TYPE);
        descriptor.setSchemaVersion(AgentVersionStorageDescriptorSerializer.SCHEMA_VERSION);
        descriptor.setSize(100L);
        descriptor.setContentDigest(
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        version.setStorage(AgentVersionStorageDescriptorSerializer.serialize(descriptor));
        version.setId(201L);
        version.setStatus("online");
        lenient().when(manager.findVersion("source", "same-name", "agent", "1.0.0"))
            .thenReturn(version);
    }
    
    @Test
    void metadataExposesOnlyFixedVersionAndNoSourceMutableRevision() throws Exception {
        AgentSummary result = adapter.getAgent("501");
        assertEquals("__nacos_ai_hub__", result.getNamespaceId());
        assertEquals("501", result.getAgentName());
        assertEquals("Agent display", result.getDisplayName());
        assertNull(result.getMetaVersion());
        assertEquals(1, result.getVersionInfo().onlineCnt());
        assertEquals(Collections.emptyMap(), result.getVersionInfo().getLabels());
        assertEquals(1, result.getVersionInfo().getOnlineVersions().size());
        assertEquals(Collections.singletonList("a2a"),
            result.getVersionInfo().getOnlineVersions().get(0).getProtocols());
        verifyNoInteractions(contents);
    }
    
    @Test
    void discoverPinsSourceVersionAndKeepsCallerReferenceUnchanged() throws Exception {
        AgentDiscoveryRequest request = request("latest");
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(Collections.singletonList("a2a"));
        request.setFilter(filter);
        AgentDiscoveryResult result = adapter.discover(request, pinned -> {
            assertEquals("source", pinned.getNamespaceId());
            assertEquals("same-name", pinned.getReference().getAgentName());
            assertEquals("1.0.0", pinned.getReference().getVersion());
            assertNull(pinned.getReference().getLabel());
            assertSame(filter, pinned.getFilter());
            return response("1.0.0");
        });
        assertEquals("__nacos_ai_hub__", result.getNamespaceId());
        assertEquals("501", result.getAgentName());
        assertEquals("latest", request.getReference().getLabel());
        assertNull(request.getReference().getVersion());
        verify(publications).inspectPinned(eq(HubResourceType.AGENT), eq(501L), isNull(), any());
        verifyNoInteractions(agents, manager, contents);
    }
    
    @Test
    void mismatchedDiscoveryVersionFailsClosed() {
        assertThrows(NacosException.class,
            () -> adapter.discover(request(null), pinned -> response("2.0.0")));
    }
    
    @Test
    void unknownHubLabelAndDisplayNameAreRejectedBeforeIo() {
        assertThrows(NacosException.class,
            () -> adapter.discover(request("stable"), pinned -> response("1.0.0")));
        assertThrows(NacosException.class, () -> adapter.getAgent("same-name"));
        verifyNoInteractions(publications, agents, contents);
    }
    
    @Test
    void versionListCannotExposeOtherSourceVersionsOrReadContent() throws Exception {
        assertEquals(1, adapter.versions("501", null, 1, 20).getTotalCount());
        assertEquals("1.0.0",
            adapter.versions("501", null, 1, 20).getPageItems().get(0).getVersion());
        assertEquals(0, adapter.versions("501", "draft", 1, 20).getTotalCount());
        assertEquals(0, adapter.versions("501", null, 2, 1).getPageItems().size());
        verifyNoInteractions(contents, agents);
    }
    
    @Test
    void replacedVersionCannotBeReturnedInOverview() {
        version.setId(999L);
        assertThrows(NacosException.class, () -> adapter.overview("501"));
    }
    
    @Test
    void searchNormalizesEmptyMetadataTagsForRadCatalogWithoutChangingSource() throws Exception {
        AgentSummary source = agents.getAgent("source", "same-name");
        source.setTags(Collections.emptyList());
        AiResource hub = new AiResource();
        hub.setId(501L);
        Page<HubResourceDirectoryService.Entry> entries = new Page<>();
        entries.setPageNumber(1);
        entries.setTotalCount(1);
        entries.setPageItems(Collections.singletonList(new HubResourceDirectoryService.Entry(hub,
            new AiResource(), HubResourceReferenceJsonUtil.build("source", 101L, "same-name",
                201L, "1.0.0"))));
        when(directory.list(eq(HubResourceType.AGENT), isNull(), isNull(), isNull(),
            eq("entry_id_ascii"), eq(1), eq(20), any())).thenReturn(entries);
        Page<AgentSummary> result = adapter.search(new AgentSearchRequest());
        assertEquals(1, result.getTotalCount());
        assertNull(result.getPageItems().get(0).getTags());
        assertEquals(Collections.emptyList(), source.getTags());
    }
    
    private AgentDiscoveryRequest request(String label) {
        AgentReference reference = new AgentReference();
        reference.setAgentName("501");
        reference.setLabel(label);
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("__nacos_ai_hub__");
        request.setReference(reference);
        return request;
    }
    
    private AgentDiscoveryResult response(String version) {
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("source");
        result.setAgentName("same-name");
        result.setVersion(version);
        result.setContentDigest(
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        result.setCallInterfaces(Collections.emptyList());
        return result;
    }
}
