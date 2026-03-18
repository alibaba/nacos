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

import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
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
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;

import java.nio.charset.StandardCharsets;

/**
 * Nacos Config based {@link AiResourceStorage} implementation.
 *
 * <p>StorageKey.key format (for Skill resources): {@code namespaceId:skillName:version:filePath}.
 * File path convention: main = {@link #getMainFilePath()}, resources = {@link #getResourceFilePath(String, String)}.</p>
 */
public class NacosConfigAiResourceStorage implements AiResourceStorage {

    public static final String TYPE = "nacos_config";

    /**
     * Build storage key for skill resources. Key format: namespaceId:skillName:version:filePath.
     *
     * @param provider    storage provider (e.g. {@link #TYPE})
     * @param namespaceId namespace
     * @param skillName   skill name
     * @param version     version
     * @param filePath    file path (use {@link #getMainFilePath()} or {@link #getResourceFilePath(String, String)})
     * @return StorageKey for route and save/get/delete
     */
    public static StorageKey buildStorageKey(String provider, String namespaceId, String skillName, String version,
            String filePath) {
        String key = namespaceId + ":" + skillName + ":" + version + ":" + filePath;
        return new StorageKey(provider, key);
    }

    /**
     * Main skill file path (dataId) for Nacos Config.
     */
    public static String getMainFilePath() {
        return SkillUtils.SKILL_MAIN_DATA_ID;
    }

    /**
     * Resource file path (dataId) for Nacos Config, from type and name.
     */
    public static String getResourceFilePath(String type, String name) {
        String resourceId = SkillUtils.generateResourceId(type, name);
        return SkillUtils.RESOURCE_DATA_ID_PREFIX + resourceId + SkillUtils.RESOURCE_DATA_ID_SUFFIX;
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
        form.setDataId(parts.dataId);
        form.setGroup(parts.group);
        form.setNamespaceId(parts.namespaceId);
        form.setContent(content == null ? StringUtils.EMPTY : new String(content, StandardCharsets.UTF_8));
        form.setSrcUser("nacos");
        form.setType(guessConfigType(parts.dataId));

        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        try {
            configOperationService.publishConfig(form, requestInfo, null);
        } catch (ConfigAlreadyExistsException alreadyExists) {
            requestInfo.setUpdateForExist(Boolean.TRUE);
            configOperationService.publishConfig(form, requestInfo, null);
        }
        if (syncEffectService != null) {
            syncEffectService.toSync(form, startTimeStamp);
        }
    }

    @Override
    public byte[] get(StorageKey storageKey) throws NacosException {
        KeyParts parts = parse(storageKey);
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                parts.dataId, parts.group, parts.namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            return null;
        }
        return response.getContent() == null ? null : response.getContent().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void delete(StorageKey storageKey) throws NacosException {
        KeyParts parts = parse(storageKey);
        configOperationService.deleteConfig(parts.dataId, parts.group, parts.namespaceId, null, null, "nacos", null);
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

    private static KeyParts parse(StorageKey storageKey) {
        if (storageKey == null || StringUtils.isBlank(storageKey.getKey())) {
            throw new IllegalArgumentException("StorageKey.key is blank");
        }
        String[] parts = storageKey.getKey().split(":", 4);
        if (parts.length != 4 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])
                || StringUtils.isBlank(parts[2]) || StringUtils.isBlank(parts[3])) {
            throw new IllegalArgumentException(
                    "Invalid StorageKey.key, expected namespaceId:skillName:version:filePath, got: "
                            + storageKey.getKey());
        }
        String namespaceId = parts[0];
        String skillName = parts[1];
        String version = parts[2];
        String filePath = parts[3];
        String group = SkillUtils.SKILL_GROUP_PREFIX + skillName + "__" + version;
        String dataId = filePath;
        return new KeyParts(namespaceId, group, dataId);
    }

    private record KeyParts(String namespaceId, String group, String dataId) {
    }
}

