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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.prompt.PromptOperationService;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Migrates prompt data from legacy storage to the new DB + SPI typed storage architecture.
 *
 * <p>Uses {@link PromptLegacyDataReader} to read legacy data. The active reader is selected
 * by {@code nacos.ai.prompt.migration.provider} (default: {@code nacos}).</p>
 *
 * @author nacos
 */
@Component
public class PromptDataMigrationTask implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PromptDataMigrationTask.class);
    
    private static final String MIGRATION_MARKER_DATA_ID = "nacos.ai.prompt.migration";
    
    private static final String MIGRATION_MARKER_GROUP = "nacos_internal";
    
    private static final long MIGRATION_MARKER_STALE_MILLIS = 10 * 60 * 1000L;
    
    private static final String RESOURCE_TYPE_PROMPT = "prompt";
    
    private static final String VERSION_STATUS_ONLINE = "online";
    
    private static final String META_STATUS_ENABLE = "enable";
    
    private static final String STORAGE_PROVIDER_NACOS_CONFIG = "nacos_config";
    
    private static final String PROMPT_STORAGE_PROVIDER_CONFIG_KEY =
        "nacos.ai.prompt.storage.provider";
    
    private static final String MIGRATION_ENABLED_KEY = "nacos.ai.prompt.migration.enabled";
    
    private static final String MIGRATION_PROVIDER_KEY = "nacos.ai.prompt.migration.provider";
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private final ExecutorService migrationExecutor =
        ExecutorFactory.Managed.newSingleExecutorService(
            PromptDataMigrationTask.class.getCanonicalName(),
            new ThreadFactoryBuilder().daemon(true).nameFormat("nacos-ai-prompt-migration-%d")
                .build());
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private final PromptOperationService promptOperationService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final List<PromptLegacyDataReader> legacyDataReaders;
    
    public PromptDataMigrationTask(AiResourcePersistService aiResourcePersistService,
        AiResourceVersionPersistService aiResourceVersionPersistService,
        @Lazy PromptOperationService promptOperationService,
        ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService,
        List<PromptLegacyDataReader> legacyDataReaders) {
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
        this.promptOperationService = promptOperationService;
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.legacyDataReaders = legacyDataReaders != null ? legacyDataReaders : new ArrayList<>();
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        boolean enabled = Boolean.parseBoolean(EnvUtil.getProperty(MIGRATION_ENABLED_KEY, "true"));
        if (!enabled) {
            LOGGER.info("Prompt data migration is disabled via {}", MIGRATION_ENABLED_KEY);
            return;
        }
        migrationExecutor.execute(this::executeMigration);
    }
    
    private PromptLegacyDataReader resolveLegacyDataReader() {
        String providerType =
            EnvUtil.getProperty(MIGRATION_PROVIDER_KEY, NacosPromptLegacyDataReader.TYPE);
        for (PromptLegacyDataReader reader : legacyDataReaders) {
            if (providerType.equals(reader.type())) {
                LOGGER.info("Using PromptLegacyDataReader: {}", reader.type());
                return reader;
            }
        }
        LOGGER.warn("No PromptLegacyDataReader found for type '{}', skip migration", providerType);
        return null;
    }
    
    private void executeMigration() {
        boolean markerCreated = false;
        try {
            PromptLegacyDataReader reader = resolveLegacyDataReader();
            if (reader == null) {
                return;
            }
            
            List<LegacyPromptData> allPrompts = reader.scanLegacyPrompts();
            if (allPrompts.isEmpty()) {
                LOGGER.info("No legacy prompt data found by reader '{}', skip migration",
                    reader.type());
                return;
            }
            
            List<LegacyPromptData> needsMigration = filterNeedsMigration(allPrompts);
            if (needsMigration.isEmpty()) {
                LOGGER.info("All {} legacy prompts already migrated, skip", allPrompts.size());
                return;
            }
            
            markerCreated = tryAcquireMigrationMarker();
            if (!markerCreated) {
                LOGGER.info("Skip prompt migration because another node is migrating");
                return;
            }
            
            needsMigration = filterNeedsMigration(allPrompts);
            if (needsMigration.isEmpty()) {
                LOGGER.info("All legacy prompts already migrated after acquiring marker");
                return;
            }
            
            LOGGER.info("Start prompt data migration: {} prompts to migrate out of {} total",
                needsMigration.size(), allPrompts.size());
            
            int migrated = 0;
            int failed = 0;
            for (LegacyPromptData prompt : needsMigration) {
                try {
                    migrateOnePrompt(prompt, reader);
                    migrated++;
                } catch (Exception e) {
                    failed++;
                    LOGGER.error("Failed to migrate prompt '{}' in namespace '{}': {}",
                        prompt.getPromptKey(),
                        prompt.getNamespaceId(), e.getMessage(), e);
                }
            }
            
            LOGGER.info("Prompt data migration completed: migrated={}, failed={}, skipped={}",
                migrated, failed, allPrompts.size() - needsMigration.size());
        } catch (Exception e) {
            LOGGER.error("Prompt data migration failed unexpectedly", e);
        } finally {
            if (markerCreated) {
                releaseMigrationMarker();
            }
        }
    }
    
    private List<LegacyPromptData> filterNeedsMigration(List<LegacyPromptData> allPrompts) {
        List<LegacyPromptData> result = new ArrayList<>();
        for (LegacyPromptData prompt : allPrompts) {
            String namespace = prompt.getNamespaceId();
            AiResource existing = aiResourcePersistService.find(namespace, prompt.getPromptKey(),
                RESOURCE_TYPE_PROMPT);
            if (existing == null) {
                result.add(prompt);
                continue;
            }
            if (prompt.getVersions() != null && !prompt.getVersions().isEmpty()) {
                if (hasUnmigratedVersions(namespace, prompt.getPromptKey(), prompt.getVersions())) {
                    result.add(prompt);
                }
            }
        }
        return result;
    }
    
    private boolean hasUnmigratedVersions(String namespace, String promptKey,
        List<String> legacyVersions) {
        Set<String> migratedVersions = new HashSet<>();
        int pageNo = 1;
        int pageSize = legacyVersions.size() + 10;
        com.alibaba.nacos.api.model.Page<AiResourceVersion> page =
            aiResourceVersionPersistService.list(
                namespace, promptKey, RESOURCE_TYPE_PROMPT, null, pageNo, pageSize);
        if (page != null && page.getPageItems() != null) {
            for (AiResourceVersion v : page.getPageItems()) {
                migratedVersions.add(v.getVersion());
            }
        }
        for (String version : legacyVersions) {
            if (!migratedVersions.contains(version)) {
                return true;
            }
        }
        return false;
    }
    
    private void migrateOnePrompt(LegacyPromptData prompt, PromptLegacyDataReader reader)
        throws Exception {
        String namespace = prompt.getNamespaceId();
        String promptKey = prompt.getPromptKey();
        
        if (prompt.getVersions() == null || prompt.getVersions().isEmpty()) {
            LOGGER.warn("Prompt '{}' in namespace '{}' has no versions, skip migration", promptKey,
                namespace);
            return;
        }
        
        Map<String, String> labels =
            prompt.getLabels() != null ? new HashMap<>(prompt.getLabels()) : new HashMap<>();
        if (StringUtils.isNotBlank(prompt.getLatestVersion()) && !labels.containsKey("latest")) {
            labels.put("latest", prompt.getLatestVersion());
        }
        
        Map<String, Object> versionInfoMap = new HashMap<>(8);
        versionInfoMap.put("labels", labels);
        versionInfoMap.put("editingVersion", null);
        versionInfoMap.put("reviewingVersion", null);
        versionInfoMap.put("onlineCnt", prompt.getVersions().size());
        
        AiResource resource = new AiResource();
        resource.setNamespaceId(namespace);
        resource.setName(promptKey);
        resource.setType(RESOURCE_TYPE_PROMPT);
        resource.setDesc(prompt.getDescription());
        resource.setStatus(META_STATUS_ENABLE);
        resource.setMetaVersion(1L);
        resource.setVersionInfo(JacksonUtils.toJson(versionInfoMap));
        resource.setBizTags(
            prompt.getBizTags() != null ? JacksonUtils.toJson(prompt.getBizTags()) : "[]");
        
        try {
            aiResourcePersistService.insert(resource);
            LOGGER.info("Migrated prompt '{}' in namespace '{}' resource record to DB", promptKey,
                namespace);
        } catch (Exception e) {
            AiResource existing =
                aiResourcePersistService.find(namespace, promptKey, RESOURCE_TYPE_PROMPT);
            if (existing != null) {
                LOGGER.info(
                    "Prompt '{}' in namespace '{}' resource record already exists, continue with version migration",
                    promptKey, namespace);
            } else {
                throw e;
            }
        }
        
        int versionsMigrated = 0;
        for (String version : prompt.getVersions()) {
            try {
                migrateOneVersion(namespace, promptKey, version, reader);
                versionsMigrated++;
            } catch (Exception e) {
                LOGGER.error("Failed to migrate prompt '{}' version '{}': {}", promptKey, version,
                    e.getMessage(), e);
            }
        }
        
        try {
            promptOperationService.refreshLatestMirror(namespace, promptKey);
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh legacy mirror for prompt '{}': {}", promptKey,
                e.getMessage());
        }
        
        LOGGER.info("Migrated prompt '{}' in namespace '{}': {}/{} versions", promptKey, namespace,
            versionsMigrated, prompt.getVersions().size());
    }
    
    /**
     * Clean up legacy storage entries for a prompt. Should be called when a prompt is deleted
     * in the new system to prevent the migration task from re-importing it on next restart.
     *
     * <p>Delegates to the active {@link PromptLegacyDataReader} implementation, which knows
     * the specific legacy storage format and location.</p>
     *
     * @param namespaceId namespace ID
     * @param promptKey   prompt key
     * @param versions    version strings to clean up
     */
    public void cleanupLegacyConfig(String namespaceId, String promptKey, List<String> versions) {
        PromptLegacyDataReader reader = resolveLegacyDataReader();
        if (reader == null) {
            return;
        }
        reader.cleanupLegacyData(namespaceId, promptKey, versions);
    }
    
    private void migrateOneVersion(String namespace, String promptKey, String version,
        PromptLegacyDataReader reader) throws Exception {
        PromptVersionInfo versionInfo = reader.readVersionContent(namespace, promptKey, version);
        if (versionInfo == null) {
            LOGGER.warn("No content found for prompt '{}' version '{}', skip", promptKey, version);
            return;
        }
        
        versionInfo.setPromptKey(promptKey);
        versionInfo.setVersion(version);
        
        // Pre-compute md5 from content without md5 field
        versionInfo.setMd5(null);
        String contentJson = JacksonUtils.toJson(versionInfo);
        String md5 = com.alibaba.nacos.common.utils.MD5Utils.md5Hex(contentJson,
            java.nio.charset.StandardCharsets.UTF_8.name());
        versionInfo.setMd5(md5);
        
        // Write to typed storage FIRST (idempotent overwrite)
        writeToTypedStorage(namespace, promptKey, version, versionInfo);
        
        // Then create DB record (idempotent: skip if exists)
        AiResourceVersion existing = aiResourceVersionPersistService.find(namespace, promptKey,
            RESOURCE_TYPE_PROMPT, version);
        if (existing != null) {
            LOGGER.debug("Prompt '{}' version '{}' already in DB, skip insert", promptKey, version);
            return;
        }
        
        AiResourceVersion versionRecord = new AiResourceVersion();
        versionRecord.setNamespaceId(namespace);
        versionRecord.setName(promptKey);
        versionRecord.setType(RESOURCE_TYPE_PROMPT);
        versionRecord.setVersion(version);
        versionRecord.setStatus(VERSION_STATUS_ONLINE);
        versionRecord.setAuthor(versionInfo.getSrcUser() != null ? versionInfo.getSrcUser() : "-");
        versionRecord.setDesc(versionInfo.getCommitMsg() != null ? versionInfo.getCommitMsg()
            : "migrated from legacy config");
        versionRecord.setStorage(buildStorageJson(namespace, promptKey, version));
        
        try {
            aiResourceVersionPersistService.insert(versionRecord);
        } catch (Exception e) {
            AiResourceVersion check = aiResourceVersionPersistService.find(namespace, promptKey,
                RESOURCE_TYPE_PROMPT, version);
            if (check != null) {
                LOGGER.debug("Prompt '{}' version '{}' inserted by another node, skip", promptKey,
                    version);
            } else {
                throw e;
            }
        }
        LOGGER.debug("Migrated prompt '{}' version '{}'", promptKey, version);
    }
    
    private boolean tryAcquireMigrationMarker() {
        for (int i = 0; i < 2; i++) {
            try {
                ConfigForm form = new ConfigForm();
                form.setNamespaceId(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
                form.setGroup(MIGRATION_MARKER_GROUP);
                form.setDataId(MIGRATION_MARKER_DATA_ID);
                form.setContent(String.valueOf(System.currentTimeMillis()));
                form.setSrcUser("nacos");
                ConfigRequestInfo requestInfo = new ConfigRequestInfo();
                requestInfo.setUpdateForExist(false);
                configOperationService.publishConfig(form, requestInfo, null);
                return true;
            } catch (ConfigAlreadyExistsException e) {
                if (isMigrationMarkerStale()) {
                    LOGGER.warn("Found stale prompt migration marker, removing and retrying");
                    releaseMigrationMarker();
                    continue;
                }
                return false;
            } catch (Exception e) {
                LOGGER.error("Failed to create prompt migration marker", e);
                return false;
            }
        }
        return false;
    }
    
    private boolean isMigrationMarkerStale() {
        try {
            ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                MIGRATION_MARKER_DATA_ID, MIGRATION_MARKER_GROUP,
                com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND
                || StringUtils.isBlank(response.getContent())) {
                return false;
            }
            long markerTime = Long.parseLong(response.getContent().trim());
            return System.currentTimeMillis() - markerTime > MIGRATION_MARKER_STALE_MILLIS;
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect prompt migration marker", e);
            return false;
        }
    }
    
    private void releaseMigrationMarker() {
        try {
            configOperationService.deleteConfig(MIGRATION_MARKER_DATA_ID, MIGRATION_MARKER_GROUP,
                com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID, null, null, "nacos",
                null);
        } catch (Exception e) {
            LOGGER.warn("Failed to delete prompt migration marker", e);
        }
    }
    
    private void writeToTypedStorage(String namespace, String promptKey, String version,
        PromptVersionInfo versionInfo) throws NacosException {
        String provider = resolveStorageProvider();
        byte[] contentBytes = JacksonUtils.toJson(versionInfo).getBytes(StandardCharsets.UTF_8);
        StorageKey storageKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespace,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, promptKey, version,
            PromptUtils.PROMPT_MAIN_DATA_ID);
        AiResourceStorageRouter.getInstance().route(storageKey).save(storageKey, contentBytes);
    }
    
    private static String buildStorageJson(String namespace, String promptKey, String version) {
        Map<String, Object> json = new HashMap<>(4);
        json.put("provider", resolveStorageProvider());
        json.put("scope", namespace + ":" + promptKey + ":" + version);
        json.put("files", Collections.singletonList(PromptUtils.PROMPT_MAIN_DATA_ID));
        return JacksonUtils.toJson(json);
    }
    
    private static String resolveStorageProvider() {
        String provider =
            EnvUtil.getProperty(PROMPT_STORAGE_PROVIDER_CONFIG_KEY, STORAGE_PROVIDER_NACOS_CONFIG);
        return StringUtils.isBlank(provider) ? STORAGE_PROVIDER_NACOS_CONFIG : provider.trim();
    }
}
