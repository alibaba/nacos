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

import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Actual target JSON formats, target-only coordinates, orphan conflicts and partial-save cleanup. */
class HubResourceTransferContentsTest {
    
    private final AiResourceStorageRouter router = mock(AiResourceStorageRouter.class);
    private final AiResourceStorage storage = mock(AiResourceStorage.class);
    private final HubResourceTransferContents contents =
        new HubResourceTransferContents(new AgentVersionStorageService(), router);
    private ConfigurableEnvironment previousEnvironment;
    
    private final Map<String, byte[]> saved = new LinkedHashMap<>();
    
    @BeforeEach
    void setUp() throws Exception {
        previousEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(router.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any()))
            .thenAnswer(call -> saved.get(call.<StorageKey>getArgument(0).getKey()));
        doAnswer(call -> {
            saved.put(call.<StorageKey>getArgument(0).getKey(), call.getArgument(1));
            return null;
        }).when(storage).save(any(), any());
        doAnswer(call -> {
            saved.remove(call.<StorageKey>getArgument(0).getKey());
            return null;
        }).when(storage).delete(any());
    }
    
    @AfterEach
    void restoreEnvironment() {
        EnvUtil.setEnvironment(previousEnvironment);
    }
    
    @Test
    void promptChangesIdentityAndDigestWithoutMutatingSource() throws Exception {
        PromptVersionInfo source = new PromptVersionInfo();
        source.setPromptKey("source-name");
        source.setVersion("1.0.0");
        source.setTemplate("Hello {{name}}");
        HubResourceDraftTransferWriter.PreparedContent plan = contents.prompt("target-ns",
            "new-name", "0.0.1", source);
        verifyNoInteractions(storage);
        String descriptor = plan.write();
        assertFalse(descriptor.contains("source-name"));
        PromptVersionInfo target =
            JacksonUtils.toObj(saved.values().iterator().next(), PromptVersionInfo.class);
        assertEquals("new-name", target.getPromptKey());
        assertEquals("0.0.1", target.getVersion());
        assertEquals(source.getTemplate(), target.getTemplate());
        String md5 = target.getMd5();
        target.setMd5(null);
        assertEquals(MD5Utils.md5Hex(JacksonUtils.toJson(target), StandardCharsets.UTF_8.name()),
            md5);
        assertEquals("source-name", source.getPromptKey());
        assertEquals("1.0.0", source.getVersion());
        plan.cleanup();
        assertEquals(0, saved.size());
    }
    
    @Test
    void agentSpecUsesFreshManifestAndResourceUniformId() throws Exception {
        AgentSpec source = agentSpec();
        HubResourceDraftTransferWriter.PreparedContent plan =
            contents.agentSpec("target-ns", "new-name", "v1", source);
        verifyNoInteractions(storage);
        plan.write();
        assertEquals(2, saved.size());
        Map<?, ?> main = null;
        Map<?, ?> resource = null;
        for (Map.Entry<String, byte[]> file : saved.entrySet()) {
            Map<?, ?> json = JacksonUtils.toObj(file.getValue(), Map.class);
            if (file.getKey().endsWith("manifest.json")) {
                main = json;
            } else {
                resource = json;
            }
        }
        assertNotNull(main);
        assertNotNull(resource);
        Map<?, ?> manifest = JacksonUtils.toObj((String) main.get("content"), Map.class);
        assertEquals("v1", manifest.get("version"));
        assertEquals("new-name", ((Map<?, ?>) manifest.get("worker")).get("suggested_name"));
        assertEquals(main.get("uniformId"),
            ((Map<?, ?>) resource.get("metadata")).get("uniformId"));
        assertEquals("", resource.get("content"));
        assertEquals(42L, source.getResource().get("one").getMetadata().get("uniformId"));
        assertFalse(source.getContent().contains("new-name"));
    }
    
    @Test
    void orphanContentFailsBeforeOverwriteAndCleanupPreservesIt() throws Exception {
        PromptVersionInfo source = new PromptVersionInfo();
        source.setTemplate("template");
        HubResourceDraftTransferWriter.PreparedContent first =
            contents.prompt("target-ns", "target", "v1", source);
        first.write();
        byte[] original = saved.values().iterator().next();
        HubResourceDraftTransferWriter.PreparedContent duplicate =
            contents.prompt("target-ns", "target", "v1", source);
        assertThrows(NacosException.class, duplicate::write);
        duplicate.cleanup();
        assertEquals(1, saved.size());
        assertEquals(original, saved.values().iterator().next());
    }
    
    @Test
    void duplicateAgentSpecStorageIdentityFailsBeforeAnyIo() {
        AgentSpec source = agentSpec();
        source.getResource().put("another-map-key", source.getResource().get("one"));
        assertThrows(IllegalArgumentException.class,
            () -> contents.agentSpec("target-ns", "target", "v1", source));
        verifyNoInteractions(storage);
    }
    
    @Test
    void ambiguousPartialSaveIsTrackedForCleanup() throws Exception {
        doAnswer(call -> {
            saved.put(call.<StorageKey>getArgument(0).getKey(), call.getArgument(1));
            throw new NacosException(500, "failed after save");
        }).when(storage).save(any(), any());
        HubResourceDraftTransferWriter.PreparedContent plan =
            contents.agentSpec("target-ns", "target", "v1", agentSpec());
        assertThrows(NacosException.class, plan::write);
        assertEquals(1, saved.size());
        plan.cleanup();
        assertEquals(0, saved.size());
    }
    
    private AgentSpec agentSpec() {
        AgentSpec spec = new AgentSpec();
        spec.setContent("{\"version\":\"1.0.0\",\"worker\":{\"suggested_name\":\"source-name\"}}");
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName("AGENTS.md");
        resource.setType("config");
        resource.setContent("");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("uniformId", 42L);
        resource.setMetadata(metadata);
        Map<String, AgentSpecResource> resources = new LinkedHashMap<>();
        resources.put("one", resource);
        spec.setResource(resources);
        return spec;
    }
    
    @Test
    void mcpGetsFreshStorageIdentityAndKeepsToolsBeforeServerWithoutNamingWrites()
        throws Exception {
        com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail source =
            new com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail();
        com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo server =
            new com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo();
        server.setNamespaceId("source-ns");
        server.setName("source-name");
        server.setId("old-id");
        server.setProtocol("stdio");
        source.setServerSpecification(server);
        source.setToolSpecification(new com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification());
        String freshId = "4d7939c0-72ea-4ef4-b232-418d1e16b45c";
        HubResourceDraftTransferWriter.PreparedContent plan =
            contents.mcp("target-ns", "target", "0.0.1",
                freshId, source, null);
        verifyNoInteractions(storage);
        String descriptor = plan.write();
        assertEquals(2, saved.size());
        assertFalse(descriptor.contains("source-ns"));
        byte[] last = null;
        for (byte[] bytes : saved.values()) {
            last = bytes;
        }
        com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo target = JacksonUtils.toObj(last,
            com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo.class);
        assertEquals(freshId, target.getId());
        assertEquals("target-ns", target.getNamespaceId());
        assertEquals("target", target.getName());
        assertEquals("0.0.1", target.getVersionDetail().getVersion());
        assertEquals(Boolean.FALSE, target.getVersionDetail().getIs_latest());
        assertNotNull(target.getToolsDescriptionRef());
        assertEquals("old-id", server.getId());
        plan.cleanup();
        assertEquals(0, saved.size());
    }
    
}
