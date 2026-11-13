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
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecQueryResult;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecStorageReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Existing AgentSpec cache and label contracts at the shared-read boundary. */
@ExtendWith(MockitoExtension.class)
class HubAgentSpecReadAdapterTest {
    
    @Mock
    private HubResourcePublicationService publications;
    
    @Mock
    private HubResourceDirectoryService directory;
    
    @Mock
    private HubResourceDiscoveryProjector projector;
    
    @Mock
    private HubResourceContentService contents;
    
    @Mock
    private AiResourceManager manager;
    
    @Mock
    private AgentSpecStorageReader storage;
    
    @Mock
    private HubResourceDownloadRecorder downloads;
    
    private HubAgentSpecReadAdapter adapter;
    
    private AiResourceVersion row;
    
    private HubResourceReference reference;
    
    @BeforeEach
    void setUp() throws Exception {
        adapter = new HubAgentSpecReadAdapter(publications, directory, projector, contents,
            manager, storage, downloads);
        reference = HubResourceReferenceJsonUtil.build("source", 101L, "same-name", 201L, "1.0.0");
        row = new AiResourceVersion();
        row.setId(201L);
        row.setStatus("online");
        row.setStorage("{\"contentMd5\":\"cached\"}");
        lenient().when(manager.findVersion("source", "same-name", "agentspec", "1.0.0"))
            .thenReturn(row);
        lenient().when(publications.inspectPinned(eq(HubResourceType.AGENTSPEC), eq(501L),
            any(), any())).thenAnswer(call -> {
                HubResourcePublicationService.PinnedPublicationReader<?> reader =
                    call.getArgument(3);
                return reader.read(new AiResource(), new AiResource(), reference);
            });
    }
    
    @Test
    void matchingMd5DoesNotReadBodyOrIncrementDownloads() throws Exception {
        AgentSpecQueryResult result = adapter.query("501", null, null, "cached");
        assertTrue(result.isNotModified());
        assertNull(result.getAgentSpec());
        assertEquals("1.0.0", result.getResolvedVersion());
        verifyNoInteractions(contents, storage, downloads);
        verify(publications).inspectPinned(eq(HubResourceType.AGENTSPEC), eq(501L), isNull(),
            any());
    }
    
    @Test
    void fullContentKeepsManifestAndSelectedVersionAndUsesInternalNamespace() throws Exception {
        AgentSpec spec = new AgentSpec();
        spec.setNamespaceId("source");
        spec.setName("same-name");
        spec.setContent("{\"version\":\"1.0.0\",\"worker\":{\"suggested_name\":\"same-name\"}}");
        when(contents.readAgentSpec(501L, "1.0.0")).thenReturn(spec);
        
        AgentSpecQueryResult result = adapter.query("501", "1.0.0", null, "old-cache");
        
        assertFalse(result.isNotModified());
        assertSame(spec, result.getAgentSpec());
        assertEquals("same-name", spec.getName());
        assertEquals(AiConstants.Hub.NAMESPACE_ID, spec.getNamespaceId());
        assertTrue(spec.getContent().contains("suggested_name"));
        assertEquals("cached", result.getMd5());
        verify(downloads).record(HubResourceType.AGENTSPEC, 501L, "1.0.0");
        verify(publications).inspectPinned(eq(HubResourceType.AGENTSPEC), eq(501L), eq("1.0.0"),
            any());
    }
    
    @Test
    void unknownLabelRetainsOriginalPriorityAndFallsBackToTheOnlySharedLatest() throws Exception {
        assertTrue(adapter.query("501", "9.9.9", "unknown-label", "cached").isNotModified());
        verify(publications).inspectPinned(eq(HubResourceType.AGENTSPEC), eq(501L), isNull(),
            any());
    }
    
    @Test
    void replacedSourceVersionCannotReturn304FromStaleDigest() {
        row.setId(202L);
        assertThrows(NacosException.class, () -> adapter.query("501", null, null, "cached"));
        verifyNoInteractions(contents, downloads);
    }
    
    @Test
    void malformedOrDisplayNameIdentityIsRejectedBeforeAnyIo() {
        for (String id : new String[] {"same-name", "0", "-1", "0501", "9223372036854775808"}) {
            assertEquals(NacosException.INVALID_PARAM,
                assertThrows(NacosException.class, () -> adapter.query(id, null, null, null))
                    .getErrCode());
        }
        verifyNoInteractions(publications, manager, contents, downloads);
    }
}
