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

import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageKeyComposer;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import org.springframework.beans.BeanUtils;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agent.storage.PreparedAgentVersionWrite;
import com.alibaba.nacos.ai.storage.AiResourceStorageUtils;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Target-only content plans retaining the canonical format of each resource type. */
@Service
public class HubResourceTransferContents {
    
    private final AgentVersionStorageService agents;
    private final AiResourceStorageRouter router;
    
    @Autowired
    public HubResourceTransferContents(AgentVersionStorageService agents) {
        this(agents, AiResourceStorageRouter.getInstance());
    }
    
    HubResourceTransferContents(AgentVersionStorageService agents, AiResourceStorageRouter router) {
        this.agents = agents;
        this.router = router;
    }
    
    /** Prepare canonical Agent bytes and a fresh target descriptor without Runtime Endpoint facts. */
    public HubResourceDraftTransferWriter.PreparedContent agent(String namespace, String name,
        String version, AgentVersionContent source) throws NacosException {
        PreparedAgentVersionWrite prepared = agents.prepare(namespace, name, version, source);
        Map<StorageKey, byte[]> files = new LinkedHashMap<>();
        files.put(new StorageKey(prepared.getDescriptor().getProvider(),
            prepared.getDescriptor().getKey()), prepared.getBytes());
        return plan(files,
            AgentVersionStorageDescriptorSerializer.serialize(prepared.getDescriptor()));
    }
    
    /** Recompute target Prompt identity and MD5 using the ordinary Prompt serialization contract. */
    public HubResourceDraftTransferWriter.PreparedContent prompt(String namespace, String name,
        String version, PromptVersionInfo source) {
        String provider = provider(Constants.Prompt.PROMPT_STORAGE_PROVIDER_CONFIG_KEY);
        PromptVersionInfo target = new PromptVersionInfo();
        target.setPromptKey(name);
        target.setVersion(version);
        target.setTemplate(source.getTemplate());
        target.setVariables(source.getVariables());
        target.setGmtModified(System.currentTimeMillis());
        target.setMd5(MD5Utils.md5Hex(JacksonUtils.toJson(target), StandardCharsets.UTF_8.name()));
        Map<StorageKey, byte[]> files = new LinkedHashMap<>();
        files.put(
            key(provider, namespace, "prompt", name, version, PromptUtils.PROMPT_MAIN_DATA_ID),
            JacksonUtils.toJsonBytes(target));
        return plan(files, descriptor(provider, namespace, name, version,
            Collections.singletonList(PromptUtils.PROMPT_MAIN_DATA_ID)));
    }
    
    /** Create independent AgentSpec main/resource objects with one fresh package uniformId. */
    public HubResourceDraftTransferWriter.PreparedContent agentSpec(String namespace, String name,
        String version, AgentSpec source) throws NacosException {
        String provider = provider(Constants.AgentSpecs.AGENTSPEC_STORAGE_PROVIDER_CONFIG_KEY);
        Map<String, Object> manifest = JacksonUtils.toObj(source.getContent(), Map.class);
        if (manifest == null) {
            throw invalid("AgentSpec manifest must be a JSON object");
        }
        // The target package identity changes; other manifest fields and dependencies remain explicit content.
        manifest.put("version", version);
        Object workerValue = manifest.get("worker");
        if (!(workerValue instanceof Map)) {
            throw invalid("AgentSpec manifest worker must be a JSON object");
        }
        {
            Map<String, Object> worker = new LinkedHashMap<>((Map<String, Object>) workerValue);
            worker.put("suggested_name", name);
            manifest.put("worker", worker);
        }
        long uniformId = System.currentTimeMillis();
        List<Map<String, String>> references = new ArrayList<>();
        Map<StorageKey, byte[]> files = new LinkedHashMap<>();
        if (source.getResource() != null) {
            for (AgentSpecResource original : source.getResource().values()) {
                if (original == null || original.getName() == null) {
                    throw invalid("AgentSpec contains an invalid resource");
                }
                SkillUtils.validatePathSafety(original.getName());
                String path = NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(
                    original.getType(), original.getName());
                AgentSpecResource target =
                    JacksonUtils.toObj(JacksonUtils.toJson(original), AgentSpecResource.class);
                Map<String, Object> metadata = target.getMetadata() == null ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(target.getMetadata());
                metadata.put("uniformId", uniformId);
                target.setMetadata(metadata);
                if (files.put(key(provider, namespace, "agentspec", name, version, path),
                    JacksonUtils.toJsonBytes(target)) != null) {
                    throw invalid("AgentSpec resources collide in target storage");
                }
                Map<String, String> reference = new LinkedHashMap<>();
                reference.put("name", target.getName());
                reference.put("type", target.getType());
                references.add(reference);
            }
        }
        Map<String, Object> main = new LinkedHashMap<>();
        main.put("name", name);
        main.put("description", source.getDescription());
        main.put("content", JacksonUtils.toJson(manifest));
        main.put("uniformId", uniformId);
        main.put("resources", references);
        files.put(key(provider, namespace, "agentspec", name, version,
            AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID), JacksonUtils.toJsonBytes(main));
        return plan(files, descriptor(provider, namespace, name, version, null));
    }
    
    /** Prepare MCP Server/Tools/Resources with fresh physical identity and no Manifest or Naming writes. */
    public HubResourceDraftTransferWriter.PreparedContent mcp(String namespace, String name,
        String version, String mcpId, McpServerVersionDetail source,
        McpServerRemoteServiceConfig selectedDependencies) {
        McpServerStorageInfo server = new McpServerStorageInfo();
        BeanUtils.copyProperties(source.getServerSpecification(), server);
        server.setNamespaceId(namespace);
        server.setName(name);
        server.setId(mcpId);
        server.setHub(null);
        server.setVersion(version);
        ServerVersionDetail targetVersion = new ServerVersionDetail();
        targetVersion.setVersion(version);
        targetVersion.setIs_latest(false);
        server.setVersionDetail(targetVersion);
        server.setRemoteServerConfig(selectedDependencies);
        boolean tools = source.getToolSpecification() != null;
        boolean resources = source.getResourceSpecification() != null;
        server.setToolsDescriptionRef(
            tools ? McpConfigUtils.formatServerToolSpecDataId(mcpId, version) : null);
        server.setResourceDescriptionRef(
            resources ? McpConfigUtils.formatServerResourceSpecDataId(mcpId, version) : null);
        McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.compose(namespace,
            mcpId, version, tools, resources);
        Map<StorageKey, byte[]> files = new LinkedHashMap<>();
        if (tools) {
            files.put(new StorageKey(descriptor.getProvider(), descriptor.getToolKey()),
                JacksonUtils.toJsonBytes(source.getToolSpecification()));
        }
        if (resources) {
            files.put(new StorageKey(descriptor.getProvider(), descriptor.getResourceKey()),
                JacksonUtils.toJsonBytes(source.getResourceSpecification()));
        }
        files.put(new StorageKey(descriptor.getProvider(), descriptor.getServerKey()),
            JacksonUtils.toJsonBytes(server));
        return plan(files, McpVersionStorageDescriptorSerializer.serialize(descriptor));
    }
    
    private HubResourceDraftTransferWriter.PreparedContent plan(Map<StorageKey, byte[]> files,
        String descriptor) {
        java.util.Set<String> coordinates = new java.util.HashSet<>();
        for (StorageKey key : files.keySet()) {
            if (!coordinates.add(key.getProvider() + ":" + key.getKey())) {
                throw new IllegalArgumentException("Target content keys collide");
            }
        }
        return new HubResourceDraftTransferWriter.PreparedContent() {
            
            private final List<StorageKey> touched = new ArrayList<>();
            
            @Override
            public String write() throws NacosException {
                // Check every key first: orphaned content is a conflict, never an overwrite opportunity.
                for (StorageKey key : files.keySet()) {
                    if (router.route(key).get(key) != null) {
                        throw new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT,
                            "Target resource content already exists");
                    }
                }
                for (Map.Entry<StorageKey, byte[]> file : files.entrySet()) {
                    touched.add(file.getKey());
                    router.route(file.getKey()).save(file.getKey(), file.getValue());
                }
                return descriptor;
            }
            
            @Override
            public void cleanup() throws NacosException {
                NacosException failure = null;
                for (StorageKey key : touched) {
                    try {
                        router.route(key).delete(key);
                    } catch (NacosException deletionFailure) {
                        if (failure == null) {
                            failure = deletionFailure;
                        } else {
                            failure.addSuppressed(deletionFailure);
                        }
                    }
                }
                if (failure != null) {
                    throw failure;
                }
            }
        };
    }
    
    private StorageKey key(String provider, String namespace, String type, String name,
        String version, String path) {
        return NacosConfigAiResourceStorage.buildStorageKey(provider, namespace, type, name,
            version, path);
    }
    
    private String descriptor(String provider, String namespace, String name, String version,
        List<String> files) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", provider);
        result.put("scope", namespace + ":" + name + ":" + version);
        if (files != null) {
            result.put("files", files);
        }
        return JacksonUtils.toJson(result);
    }
    
    private String provider(String property) {
        String provider =
            AiResourceStorageUtils.resolveProvider(property, NacosConfigAiResourceStorage.TYPE);
        if (!provider.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid target storage provider");
        }
        return provider;
    }
    
    private NacosApiException invalid(String message) {
        return new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
