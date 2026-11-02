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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.event.AiResourceChangeOperation;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.agent.AgentVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionContentSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageTestUtils;
import com.alibaba.nacos.ai.service.agent.storage.PreparedAgentVersionWrite;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceChangeNotifier;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class A2aSecurityReadRepairTest {
    
    private static final String RAW = "{\"name\":\"demo\",\"version\":\"1.0.0\","
        + "\"url\":\"https://declared.example/a2a\",\"unknown\":{\"keep\":true},"
        + "\"securityRequirements\":[{\"oauth2\":[\"read\"]}]}";
    
    private ConfigInfoPersistService configStore;
    
    private AiResourceVersionPersistService versionStore;
    
    private AgentVersionStorageService storageService;
    
    private AiResourceChangeNotifier notifier;
    
    private ExecutorService executor;
    
    private A2aSecurityReadRepair repair;
    
    private List<Runnable> tasks;
    
    @BeforeEach
    void setUp() {
        configStore = mock(ConfigInfoPersistService.class);
        versionStore = mock(AiResourceVersionPersistService.class);
        storageService = mock(AgentVersionStorageService.class);
        notifier = mock(AiResourceChangeNotifier.class);
        executor = mock(ExecutorService.class);
        tasks = new ArrayList<>();
        doAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));
        repair = new A2aSecurityReadRepair(configStore, versionStore, storageService, notifier,
            executor);
    }
    
    @Test
    void legacyReadReturnsStandardThenRepairsOnlyObservedRawDefinition() {
        String response = repair.readLegacy("public", "demo-1.0.0", RAW);
        assertStandard(response);
        verifyNoInteractions(configStore);
        assertEquals(1, tasks.size());
        ConfigAllInfo original = originalConfig();
        when(configStore.findConfigAllInfo("demo-1.0.0", Constants.A2A.AGENT_VERSION_GROUP,
            "public"))
            .thenReturn(original);
        when(configStore.updateConfigInfoCas(any(), isNull(), anyString(), any()))
            .thenReturn(new ConfigOperateResult(1L, 123L));
        try (MockedStatic<ConfigChangePublisher> publisher =
            mockStatic(ConfigChangePublisher.class)) {
            tasks.remove(0).run();
            ArgumentCaptor<ConfigInfo> written = ArgumentCaptor.forClass(ConfigInfo.class);
            verify(configStore).updateConfigInfoCas(written.capture(), isNull(), anyString(),
                any());
            assertEquals(original.getMd5(), written.getValue().getMd5());
            assertEquals(response, written.getValue().getContent());
            assertEquals("https://declared.example/a2a",
                JsonUtils.toObj(response, Map.class).get("url"));
            assertEquals(Collections.singletonMap("keep", true),
                JsonUtils.toObj(response, Map.class).get("unknown"));
            publisher.verify(
                () -> ConfigChangePublisher.notifyConfigChange(any(ConfigDataChangeEvent.class)));
        }
        repair.readLegacy("public", "demo-1.0.0", response);
        assertTrue(tasks.isEmpty(), "already normalized reads must not schedule writes");
    }
    
    @Test
    void legacyChangedOrDeletedDataIsNeverOverwrittenOrCreated() {
        ConfigAllInfo changed = originalConfig();
        changed.setMd5("changed");
        when(configStore.findConfigAllInfo(anyString(), anyString(), anyString()))
            .thenReturn(changed, null);
        for (int i = 0; i < 2; i++) {
            assertStandard(repair.readLegacy("public", "demo-1.0.0", RAW));
            tasks.remove(0).run();
        }
        verify(configStore, never()).updateConfigInfoCas(any(), any(), any(), any());
        verify(configStore, never()).insertOrUpdateCas(any(), any(), any(), any());
    }
    
    @Test
    void legacyCasConflictDoesNotNotifyAndLaterReadCanRetry() {
        when(configStore.findConfigAllInfo(anyString(), anyString(), anyString()))
            .thenReturn(originalConfig());
        when(configStore.updateConfigInfoCas(any(), any(), any(), any()))
            .thenReturn(new ConfigOperateResult(false));
        try (MockedStatic<ConfigChangePublisher> publisher =
            mockStatic(ConfigChangePublisher.class)) {
            repair.readLegacy("public", "demo-1.0.0", RAW);
            tasks.remove(0).run();
            publisher.verifyNoInteractions();
        }
        repair.readLegacy("public", "demo-1.0.0", RAW);
        assertEquals(1, tasks.size());
    }
    
    @Test
    void failedOrRejectedRepairDoesNotFailReadsAndIsRetryable() {
        when(configStore.findConfigAllInfo(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("unavailable"));
        assertStandard(repair.readLegacy("public", "demo-1.0.0", RAW));
        tasks.remove(0).run();
        assertStandard(repair.readLegacy("public", "demo-1.0.0", RAW));
        assertEquals(1, tasks.size());
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        assertStandard(repair.readLegacy("public", "another", RAW));
        assertStandard(repair.readLegacy("public", "another", RAW));
        verify(executor, times(4)).execute(any());
    }
    
    @Test
    void duplicateAndOverflowReadsReturnNormallyWithoutUnboundedTasks() {
        for (int i = 0; i < 100; i++) {
            assertStandard(repair.readLegacy("public", "same", RAW));
        }
        assertEquals(1, tasks.size());
        for (int i = 0; i < 100; i++) {
            assertStandard(repair.readLegacy("public", "agent-" + i, RAW));
        }
        assertEquals(64, tasks.size());
    }
    
    @Test
    void malformedRequirementsDoNotScheduleLossyRepairs() {
        assertThrows(IllegalArgumentException.class,
            () -> repair.readLegacy("public", "demo",
                "{\"securityRequirements\":[{\"oauth2\":null}]}"));
        assertTrue(tasks.isEmpty());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void canonicalRepairWritesSeparateVerifiedObjectThenConditionallySwitchesPointer(
        boolean success)
        throws Exception {
        AgentVersionContent content = content();
        AiResourceVersion row = row(content);
        AgentVersionStorageDescriptor old =
            AgentVersionStorageDescriptorSerializer.deserialize(row.getStorage());
        when(storageService.prepareRepair(any(), anyString(), anyString(), any())).thenAnswer(
            invocation -> new AgentVersionStorageService().prepareRepair(invocation.getArgument(0),
                invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
        repair.readCanonical(row, old, content);
        assertStandard(JsonUtils.toJson(content.getCallInterfaces().get(0).getNativeDescriptor()));
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
        when(versionStore.find("public", "demo", "agent", "1.0.0")).thenReturn(row);
        when(versionStore.replaceStorageIfUnchanged(eq(7L), eq(row.getStorage()), anyString()))
            .thenReturn(success);
        tasks.remove(0).run();
        ArgumentCaptor<PreparedAgentVersionWrite> write =
            ArgumentCaptor.forClass(PreparedAgentVersionWrite.class);
        InOrder order = inOrder(storageService, versionStore, notifier);
        order.verify(storageService).save(write.capture());
        ArgumentCaptor<AgentVersionStorageDescriptor> verified =
            ArgumentCaptor.forClass(AgentVersionStorageDescriptor.class);
        order.verify(storageService).load(verified.capture());
        assertEquals(write.getValue().getDescriptor().getKey(), verified.getValue().getKey());
        assertEquals(write.getValue().getDescriptor().getContentDigest(),
            verified.getValue().getContentDigest());
        order.verify(versionStore).replaceStorageIfUnchanged(eq(7L), eq(row.getStorage()),
            anyString());
        if (success) {
            order.verify(notifier).notifyChanged("public", "agent", "demo",
                AiResourceChangeOperation.UPDATE, true);
        } else {
            verifyNoInteractions(notifier);
        }
        assertNotEquals(old.getKey(), write.getValue().getDescriptor().getKey());
        assertEquals(old.getProvider(), write.getValue().getDescriptor().getProvider());
        assertEquals(AgentVersionContentSerializer.digest(write.getValue().getBytes()),
            write.getValue().getDescriptor().getContentDigest());
        assertEquals(write.getValue().getBytes().length,
            write.getValue().getDescriptor().getSize());
        assertEquals("online", row.getStatus());
        assertEquals("publisher", row.getAuthor());
        verify(storageService, never()).delete(any());
        repair.readCanonical(row, old, content);
        assertTrue(tasks.isEmpty());
    }
    
    @Test
    void canonicalConcurrentReplacementSkipsStorageWrites() throws Exception {
        AgentVersionContent content = content();
        AiResourceVersion row = row(content);
        AgentVersionStorageDescriptor descriptor =
            AgentVersionStorageDescriptorSerializer.deserialize(row.getStorage());
        when(storageService.prepareRepair(any(), anyString(), anyString(), any()))
            .thenReturn(mock(PreparedAgentVersionWrite.class));
        repair.readCanonical(row, descriptor, content);
        AiResourceVersion replacement = row(content);
        replacement.setId(8L);
        when(versionStore.find("public", "demo", "agent", "1.0.0")).thenReturn(replacement);
        tasks.remove(0).run();
        verify(storageService, never()).save(any(PreparedAgentVersionWrite.class));
        verify(versionStore, never()).replaceStorageIfUnchanged(anyLong(), anyString(),
            anyString());
    }
    
    @Test
    void canonicalStorageFailureLeavesOriginalPointerAndDoesNotFailQuery() throws Exception {
        AgentVersionContent content = content();
        AiResourceVersion row = row(content);
        AgentVersionStorageDescriptor descriptor =
            AgentVersionStorageDescriptorSerializer.deserialize(row.getStorage());
        PreparedAgentVersionWrite prepared =
            AgentVersionStorageTestUtils.prepare("public", "demo", "0.0.0-repair", content);
        when(storageService.prepareRepair(any(), anyString(), anyString(), any()))
            .thenReturn(prepared);
        when(versionStore.find("public", "demo", "agent", "1.0.0")).thenReturn(row);
        doThrow(new IllegalStateException("unavailable")).when(storageService).save(prepared);
        repair.readCanonical(row, descriptor, content);
        assertStandard(JsonUtils.toJson(content.getCallInterfaces().get(0).getNativeDescriptor()));
        tasks.remove(0).run();
        verify(versionStore, never()).replaceStorageIfUnchanged(anyLong(), anyString(),
            anyString());
        verifyNoInteractions(notifier);
    }
    
    private ConfigAllInfo originalConfig() {
        ConfigAllInfo result = new ConfigAllInfo();
        result.setDataId("demo-1.0.0");
        result.setGroup(Constants.A2A.AGENT_VERSION_GROUP);
        result.setTenant("public");
        result.setContent(RAW);
        result.setMd5(MD5Utils.md5Hex(RAW, "UTF-8"));
        return result;
    }
    
    private AgentVersionContent content() {
        AgentCallInterface call = new AgentCallInterface();
        call.setProtocol("a2a");
        call.setProtocolVersion("1.0");
        call.setDescriptorMediaType("application/json");
        call.setNativeDescriptor(JsonUtils.toObj(RAW, Map.class));
        call.setEndpointSourceOrder(Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME));
        call.setEndpointSets(Collections.emptyList());
        return new AgentVersionContent(Collections.singletonList(call));
    }
    
    private AiResourceVersion row(AgentVersionContent content) {
        PreparedAgentVersionWrite original =
            AgentVersionStorageTestUtils.prepare("public", "demo", "1.0.0", content);
        AiResourceVersion row = new AiResourceVersion();
        row.setId(7L);
        row.setNamespaceId("public");
        row.setName("demo");
        row.setType("agent");
        row.setVersion("1.0.0");
        row.setStatus("online");
        row.setAuthor("publisher");
        byte[] historicalBytes = JsonUtils.toJsonBytes(content);
        AgentVersionStorageDescriptor descriptor = original.getDescriptor();
        descriptor.setContentDigest(AgentVersionContentSerializer.digest(historicalBytes));
        descriptor.setSize((long) historicalBytes.length);
        row.setStorage(AgentVersionStorageDescriptorSerializer.serialize(descriptor));
        return row;
    }
    
    private void assertStandard(String json) {
        Map<?, ?> card = JsonUtils.toObj(json, Map.class);
        assertEquals(
            JsonUtils.toObj("[{\"schemes\":{\"oauth2\":{\"list\":[\"read\"]}}}]", List.class),
            card.get("securityRequirements"));
        assertEquals(JsonUtils.toObj("[{\"oauth2\":[\"read\"]}]", List.class),
            card.get("security"));
    }
}
