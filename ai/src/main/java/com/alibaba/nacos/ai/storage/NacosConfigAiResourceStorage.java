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

package com.alibaba.nacos.ai.storage;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.api.ai.model.NacosAiConfigKeyCodec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.config.server.utils.ConfigPersistContext;

import java.nio.charset.StandardCharsets;

/**
 * Nacos Config based {@link AiResourceStorage} implementation.
 *
 * <p>Supports Skill, AgentSpec, Prompt, Agent Version and allowlisted MCP resources.
 * StorageKey.key format:
 * <ul>
 *   <li>Legacy (Skill): {@code namespaceId:name:version:filePath} (4-part, defaults to skill__ prefix)</li>
 *   <li>Typed: {@code namespaceId:resourceType:name:version:filePath} (5-part, resourceType = "skill", "agentspec" or "prompt")</li>
 *   <li>Agent Version: {@code namespaceId:agent-version:dataId} (3-part opaque key)</li>
 *   <li>MCP Version content: {@code namespaceId:mcp-owned-group:dataId} (3-part opaque key)</li>
 * </ul>
 * File path convention: main = {@link #getMainFilePath()} / {@link #getMainFilePath(String)},
 * resources = {@link #getResourceFilePath(String, String)} / {@link #getAgentSpecResourceFilePath(String, String)},
 * manifest = {@link #buildManifestStorageKey(String, String, String)}.</p>
 */
public class NacosConfigAiResourceStorage implements AiResourceStorage {
    
    public static final String TYPE = "nacos_config";
    
    /** Resource type identifier for Skill storage keys. */
    public static final String RESOURCE_TYPE_SKILL = "skill";
    
    /** Resource type identifier for AgentSpec storage keys. */
    public static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    /** Resource type identifier for Prompt storage keys. */
    public static final String RESOURCE_TYPE_PROMPT = "prompt";
    
    private static final String AGENT_VERSION_GROUP = "agent-version";
    
    private static final String AGENT_VERSION_DATA_ID_PREFIX = "agent__";
    
    private static final String AGENT_VERSION_DATA_ID_SEPARATOR = "__";
    
    private static final String AGENT_VERSION_DATA_ID_SUFFIX = ".json";
    
    private static final int MAX_ENCODED_AGENT_ID_LENGTH = 260;
    
    /**
     * Build storage key for Skill resources (legacy 4-part format).
     * Key format: namespaceId:skillName:version:filePath.
     * Kept for backward compatibility; new Skill code may also use
     * {@link #buildStorageKey(String, String, String, String, String, String)} with {@link #RESOURCE_TYPE_SKILL}.
     *
     * @param provider    storage provider (e.g. {@link #TYPE})
     * @param namespaceId namespace
     * @param skillName   skill name
     * @param version     version
     * @param filePath    file path (use {@link #getResourceFilePath(String, String)})
     * @return StorageKey for route and save/get/delete
     */
    public static StorageKey buildStorageKey(String provider, String namespaceId, String skillName,
        String version,
        String filePath) {
        String key = namespaceId + ":" + skillName + ":" + version + ":" + filePath;
        return new StorageKey(provider, key);
    }
    
    /**
     * Build storage key with explicit resource type (5-part format).
     * Key format: namespaceId:resourceType:name:version:filePath.
     *
     * @param provider     storage provider (e.g. {@link #TYPE})
     * @param namespaceId  namespace
     * @param resourceType resource type ({@link #RESOURCE_TYPE_SKILL}, {@link #RESOURCE_TYPE_AGENTSPEC} or {@link #RESOURCE_TYPE_PROMPT})
     * @param name         resource name (skill name or agentspec name)
     * @param version      version
     * @param filePath     file path (use {@link #getMainFilePath(String)} or resource file path helpers)
     * @return StorageKey for route and save/get/delete
     */
    public static StorageKey buildStorageKey(String provider, String namespaceId,
        String resourceType, String name,
        String version, String filePath) {
        String key = namespaceId + ":" + resourceType + ":" + name + ":" + version + ":" + filePath;
        return new StorageKey(provider, key);
    }
    
    /**
     * Main Skill file path (dataId) for Nacos Config.
     */
    public static String getMainFilePath() {
        return SkillUtils.SKILL_MAIN_DATA_ID;
    }
    
    /**
     * Main file path (dataId) for a given main dataId. Use this for AgentSpec or other resource types.
     *
     * @param mainDataId the main dataId (e.g. {@link AgentSpecUtils#AGENTSPEC_MAIN_DATA_ID})
     * @return the main dataId as-is
     */
    public static String getMainFilePath(String mainDataId) {
        return mainDataId;
    }
    
    /**
     * Skill resource file path (dataId) for Nacos Config, from type and name.
     * Uses {@link SkillUtils} for resource ID generation.
     */
    public static String getResourceFilePath(String type, String name) {
        String resourceId = SkillUtils.generateResourceId(type, name);
        return SkillUtils.RESOURCE_DATA_ID_PREFIX + resourceId + SkillUtils.RESOURCE_DATA_ID_SUFFIX;
    }
    
    /**
     * AgentSpec resource file path (dataId) for Nacos Config, from type and name.
     * Uses {@link AgentSpecUtils} for resource ID generation.
     *
     * @param type resource type (can be null or empty)
     * @param name resource name
     * @return resource dataId
     */
    public static String getAgentSpecResourceFilePath(String type, String name) {
        String resourceId = AgentSpecUtils.generateResourceId(type, name);
        return AgentSpecUtils.RESOURCE_DATA_ID_PREFIX + resourceId
            + AgentSpecUtils.RESOURCE_DATA_ID_SUFFIX;
    }
    
    /**
     * Build StorageKey for skill manifest (index) config. The version part is left blank so the
     * config group has no version suffix, i.e. group = "skill_{skillName}".
     *
     * @param provider    storage provider (e.g. {@link #TYPE})
     * @param namespaceId namespace
     * @param skillName   skill name
     * @return StorageKey for manifest config
     */
    public static StorageKey buildManifestStorageKey(String provider, String namespaceId,
        String skillName) {
        String key = namespaceId + ":" + skillName + "::" + SkillUtils.SKILL_INDEX_DATA_ID;
        return new StorageKey(provider, key);
    }
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final SyncEffectService syncEffectService;
    
    public NacosConfigAiResourceStorage(ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService, SyncEffectService syncEffectService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.syncEffectService = syncEffectService;
    }
    
    @Override
    public String type() {
        return TYPE;
    }
    
    @Override
    public void save(StorageKey storageKey, byte[] content) throws NacosException {
        long startTimeStamp = System.currentTimeMillis();
        KeyParts parts = parse(storageKey);
        ConfigForm form = new ConfigForm();
        String physicalDataId = toPhysicalDataId(parts);
        String physicalGroup = toPhysicalGroup(parts);
        form.setDataId(physicalDataId);
        form.setGroup(physicalGroup);
        form.setNamespaceId(parts.namespaceId());
        form.setContent(
            content == null ? StringUtils.EMPTY : new String(content, StandardCharsets.UTF_8));
        form.setSrcUser("nacos");
        form.setType(guessConfigType(parts.dataId()));
        
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        try (ConfigPersistContext.Guard ignored = ConfigPersistContext.withSkipHistory()) {
            try {
                configOperationService.publishConfig(form, requestInfo, null);
            } catch (ConfigAlreadyExistsException alreadyExists) {
                requestInfo.setUpdateForExist(Boolean.TRUE);
                configOperationService.publishConfig(form, requestInfo, null);
            }
        }
        
        // Keep compatibility: single save still waits for effect.
        // Batch writers should call SyncEffectService#toSyncConcurrent at higher level.
        if (syncEffectService != null) {
            syncEffectService.toSync(form, startTimeStamp);
        }
    }
    
    @Override
    public byte[] get(StorageKey storageKey) throws NacosException {
        KeyParts parts = parse(storageKey);
        String physicalDataId = toPhysicalDataId(parts);
        String physicalGroup = toPhysicalGroup(parts);
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
            physicalDataId, physicalGroup, parts.namespaceId());
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            return null;
        }
        return response.getContent() == null ? null
            : response.getContent().getBytes(StandardCharsets.UTF_8);
    }
    
    @Override
    public void delete(StorageKey storageKey) throws NacosException {
        KeyParts parts = parse(storageKey);
        String physicalDataId = toPhysicalDataId(parts);
        String physicalGroup = toPhysicalGroup(parts);
        try (ConfigPersistContext.Guard ignored = ConfigPersistContext.withSkipHistory()) {
            configOperationService.deleteConfig(physicalDataId, physicalGroup, parts.namespaceId(),
                null, null, "nacos",
                null);
        }
    }
    
    private static String guessConfigType(String dataId) {
        if (StringUtils.isBlank(dataId)) {
            return ConfigType.TEXT.getType();
        }
        if (dataId.endsWith(".json")) {
            return ConfigType.JSON.getType();
        }
        if (dataId.endsWith(".yaml") || dataId.endsWith(".yml")) {
            return ConfigType.YAML.getType();
        }
        if (dataId.endsWith(".xml")) {
            return ConfigType.XML.getType();
        }
        if (dataId.endsWith(".properties")) {
            return ConfigType.PROPERTIES.getType();
        }
        return ConfigType.TEXT.getType();
    }
    
    /**
     * Parse StorageKey into KeyParts. Supports five key formats:
     * <ul>
     *   <li>Agent Version: {@code namespaceId:agent-version:dataId} → group = agent-version</li>
     *   <li>MCP content: {@code namespaceId:mcp-owned-group:dataId} → exact legacy coordinate</li>
     *   <li>Legacy 4-part (Skill): {@code namespaceId:name:version:filePath} → group = skill__{name}__{version}</li>
     *   <li>Legacy 4-part manifest: {@code namespaceId:name::filePath} (blank version) → group = skill_{name}</li>
     *   <li>Typed 5-part: {@code namespaceId:resourceType:name:version:filePath} → group = {prefix}{name}__{version}</li>
     * </ul>
     * The resource type determines the group prefix: "skill" → "skill_", "agentspec" → "agentspec__".
     */
    static KeyParts parse(StorageKey storageKey) {
        if (storageKey == null || StringUtils.isBlank(storageKey.getKey())) {
            throw new IllegalArgumentException("StorageKey.key is blank");
        }
        KeyParts mcpParts = parseMcpStorageKey(storageKey.getKey());
        if (mcpParts != null) {
            return mcpParts;
        }
        String[] agentVersionParts = storageKey.getKey().split(":", -1);
        if (agentVersionParts.length == 3 && AGENT_VERSION_GROUP.equals(agentVersionParts[1])
            && agentVersionParts[2].startsWith(AGENT_VERSION_DATA_ID_PREFIX)) {
            String namespaceId = agentVersionParts[0];
            String dataId = agentVersionParts[2];
            AgentValidationUtils.validateNamespaceId(namespaceId);
            validateAgentVersionDataId(dataId);
            return new KeyParts(namespaceId, AGENT_VERSION_GROUP, AGENT_VERSION_GROUP, dataId);
        }
        String[] parts = storageKey.getKey().split(":", 5);
        if (parts.length == 5 && !StringUtils.isBlank(parts[0]) && !StringUtils.isBlank(parts[1])
            && !StringUtils.isBlank(parts[2]) && !StringUtils.isBlank(parts[3])
            && !StringUtils.isBlank(parts[4])) {
            // 5-part typed format: namespaceId:resourceType:name:version:filePath
            String namespaceId = parts[0];
            String resourceType = parts[1];
            String name = parts[2];
            String version = parts[3];
            String filePath = parts[4];
            String group;
            String groupPrefix;
            if (RESOURCE_TYPE_AGENTSPEC.equals(resourceType)) {
                groupPrefix = AgentSpecUtils.AGENTSPEC_GROUP_PREFIX;
                group = AgentSpecUtils.buildAgentSpecVersionGroup(name, version);
            } else if (RESOURCE_TYPE_SKILL.equals(resourceType)) {
                groupPrefix = SkillUtils.SKILL_GROUP_PREFIX;
                group = SkillUtils.buildSkillVersionGroup(name, version);
            } else if (RESOURCE_TYPE_PROMPT.equals(resourceType)) {
                groupPrefix = PromptUtils.PROMPT_GROUP_PREFIX;
                group = PromptUtils.buildPromptVersionGroup(name, version);
            } else {
                throw new IllegalArgumentException("Unknown resource type: " + resourceType);
            }
            return new KeyParts(namespaceId, group, groupPrefix, filePath);
        }
        // Fall back to legacy 4-part format (Skill): namespaceId:name:version:filePath
        String[] legacyParts = storageKey.getKey().split(":", 4);
        // parts[2] (version) may be blank for manifest keys
        if (legacyParts.length != 4 || StringUtils.isBlank(legacyParts[0])
            || StringUtils.isBlank(legacyParts[1])
            || StringUtils.isBlank(legacyParts[3])) {
            throw new IllegalArgumentException(
                "Invalid StorageKey.key, expected namespaceId:name:version:filePath or "
                    + "namespaceId:resourceType:name:version:filePath, got: "
                    + storageKey.getKey());
        }
        String namespaceId = legacyParts[0];
        String skillName = legacyParts[1];
        String version = legacyParts[2];
        String filePath = legacyParts[3];
        String group;
        if (StringUtils.isBlank(version)) {
            group = SkillUtils.buildSkillGroup(skillName);
        } else {
            group = SkillUtils.buildSkillVersionGroup(skillName, version);
        }
        return new KeyParts(namespaceId, group, SkillUtils.SKILL_GROUP_PREFIX, filePath);
    }
    
    private static KeyParts parseMcpStorageKey(String key) {
        int namespaceSeparator = key.indexOf(':');
        if (namespaceSeparator < 0) {
            return null;
        }
        int groupSeparator = key.indexOf(':', namespaceSeparator + 1);
        if (groupSeparator < 0) {
            return null;
        }
        String group = key.substring(namespaceSeparator + 1, groupSeparator);
        if (!isMcpStorageGroup(group)) {
            return null;
        }
        String namespaceId = key.substring(0, namespaceSeparator);
        String dataId = key.substring(groupSeparator + 1);
        if (!isMcpStorageDataId(group, dataId)) {
            return null;
        }
        AgentValidationUtils.validateNamespaceId(namespaceId);
        return new KeyParts(namespaceId, group, group, dataId);
    }
    
    private static boolean isMcpStorageDataId(String group, String dataId) {
        String suffix;
        if (Constants.MCP_SERVER_GROUP.equals(group)) {
            suffix = Constants.MCP_SERVER_SPEC_DATA_ID_SUFFIX;
        } else if (Constants.MCP_SERVER_TOOL_GROUP.equals(group)) {
            suffix = Constants.MCP_SERVER_TOOL_DATA_ID_SUFFIX;
        } else {
            suffix = Constants.MCP_SERVER_RESOURCE_DATA_ID_SUFFIX;
        }
        return dataId.length() > suffix.length() && dataId.endsWith(suffix);
    }
    
    private static boolean isMcpStorageGroup(String group) {
        return Constants.MCP_SERVER_GROUP.equals(group)
            || Constants.MCP_SERVER_TOOL_GROUP.equals(group)
            || Constants.MCP_SERVER_RESOURCE_GROUP.equals(group);
    }
    
    private static String toPhysicalDataId(KeyParts parts) {
        return isMcpStorageGroup(parts.group()) ? parts.dataId()
            : NacosAiConfigKeyCodec.toPhysicalDataId(parts.dataId());
    }
    
    private static String toPhysicalGroup(KeyParts parts) {
        return isMcpStorageGroup(parts.group()) ? parts.group()
            : NacosAiConfigKeyCodec.toPhysicalGroup(parts.group(), parts.groupPrefix());
    }
    
    private static void validateAgentVersionDataId(String dataId) {
        if (dataId == null || !dataId.startsWith(AGENT_VERSION_DATA_ID_PREFIX)
            || !dataId.endsWith(AGENT_VERSION_DATA_ID_SUFFIX)) {
            throw new IllegalArgumentException("Invalid Agent Version dataId: " + dataId);
        }
        int separatorIndex = dataId.indexOf(AGENT_VERSION_DATA_ID_SEPARATOR,
            AGENT_VERSION_DATA_ID_PREFIX.length());
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Invalid Agent Version dataId: " + dataId);
        }
        String encodedAgentId = dataId.substring(AGENT_VERSION_DATA_ID_PREFIX.length(),
            separatorIndex);
        String version = dataId.substring(separatorIndex + AGENT_VERSION_DATA_ID_SEPARATOR.length(),
            dataId.length() - AGENT_VERSION_DATA_ID_SUFFIX.length());
        validateEncodedAgentId(encodedAgentId);
        AgentValidationUtils.validateVersion(version);
    }
    
    private static void validateEncodedAgentId(String encodedAgentId) {
        if (StringUtils.isBlank(encodedAgentId)
            || encodedAgentId.length() > MAX_ENCODED_AGENT_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid encodedAgentId: " + encodedAgentId);
        }
        for (int i = 0; i < encodedAgentId.length(); i++) {
            char current = encodedAgentId.charAt(i);
            boolean asciiLetterOrDigit = current >= 'A' && current <= 'Z'
                || current >= 'a' && current <= 'z'
                || current >= '0' && current <= '9';
            if (!asciiLetterOrDigit && current != '-') {
                throw new IllegalArgumentException("Invalid encodedAgentId: " + encodedAgentId);
            }
        }
    }
    
    record KeyParts(String namespaceId, String group, String groupPrefix, String dataId) {
    }
}
