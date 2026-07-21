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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.ard.ArdEntryBuilder;
import com.alibaba.nacos.ai.service.ard.ArdIndexBuildService;
import com.alibaba.nacos.ai.service.ard.ArdIndexRepository;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reconciles ARD indexes for resources that existed before ARD indexing was enabled.
 *
 * @author nacos
 */
@Component
@ConditionalOnArdEnabled
public class ArdIndexBackfillTask implements ApplicationListener<ApplicationReadyEvent> {
    
    static final String BACKFILL_ENABLED_KEY = "nacos.ai.ard.index.backfill.enabled";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ArdIndexBackfillTask.class);
    
    private static final String BACKFILL_MARKER_DATA_ID = "nacos.ai.ard.index.backfill";
    
    private static final String BACKFILL_MARKER_GROUP = "nacos_internal";
    
    private static final long BACKFILL_MARKER_STALE_MILLIS = 10 * 60 * 1000L;
    
    private static final int SCAN_PAGE_SIZE = 100;
    
    private static final String RESOURCE_TYPE_MCP = "mcp";
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private final ExecutorService backfillExecutor =
        ExecutorFactory.Managed.newSingleExecutorService(
            ArdIndexBackfillTask.class.getCanonicalName(),
            new ThreadFactoryBuilder().daemon(true).nameFormat("nacos-ai-ard-backfill-%d").build());
    
    private final AiResourceManager resourceManager;
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final ArdIndexRepository repository;
    
    private final ArdIndexBuildService indexBuildService;
    
    private final NamespaceOperationService namespaceOperationService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final ArdEntryBuilder entryBuilder = new ArdEntryBuilder();
    
    public ArdIndexBackfillTask(AiResourceManager resourceManager,
        McpServerOperationService mcpServerOperationService, ArdIndexRepository repository,
        ArdIndexBuildService indexBuildService, NamespaceOperationService namespaceOperationService,
        ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService) {
        this.resourceManager = resourceManager;
        this.mcpServerOperationService = mcpServerOperationService;
        this.repository = repository;
        this.indexBuildService = indexBuildService;
        this.namespaceOperationService = namespaceOperationService;
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        boolean enabled = Boolean.parseBoolean(EnvUtil.getProperty(BACKFILL_ENABLED_KEY, "true"));
        if (!enabled) {
            LOGGER.info("ARD index backfill is disabled via {}", BACKFILL_ENABLED_KEY);
            return;
        }
        backfillExecutor.execute(this::executeBackfill);
    }
    
    private void executeBackfill() {
        boolean markerCreated = false;
        try {
            markerCreated = tryAcquireBackfillMarker();
            if (!markerCreated) {
                LOGGER.info("Skip ARD index backfill because another node is processing it");
                return;
            }
            BackfillStats stats = new BackfillStats();
            for (Namespace namespace : getNamespaces()) {
                String namespaceId = namespace.getNamespace();
                backfillAiResources(namespaceId, Constants.Skills.RESOURCE_TYPE_SKILL, stats);
                backfillAiResources(namespaceId,
                    NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, stats);
                backfillMcpServers(namespaceId, stats);
            }
            LOGGER.info(
                "ARD index backfill completed: scanned={}, rebuilt={}, skipped={}, failed={}",
                stats.scanned, stats.rebuilt, stats.skipped, stats.failed);
        } catch (Exception e) {
            LOGGER.error("ARD index backfill failed unexpectedly", e);
        } finally {
            if (markerCreated) {
                releaseBackfillMarker();
            }
        }
    }
    
    private List<Namespace> getNamespaces() {
        try {
            List<Namespace> namespaces = namespaceOperationService.getNamespaceList();
            if (namespaces != null && !namespaces.isEmpty()) {
                return namespaces;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to list namespaces for ARD index backfill", e);
        }
        return Collections.singletonList(new Namespace(
            com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID, "public"));
    }
    
    private void backfillAiResources(String namespaceId, String resourceType,
        BackfillStats stats) {
        int pageNo = 1;
        while (true) {
            Page<AiResource> page = resourceManager.listMetaByType(namespaceId, resourceType, null,
                null, pageNo, SCAN_PAGE_SIZE);
            List<AiResource> resources = page == null ? null : page.getPageItems();
            if (resources == null || resources.isEmpty()) {
                return;
            }
            for (AiResource resource : resources) {
                backfillAiResource(namespaceId, resource, stats);
            }
            if (resources.size() < SCAN_PAGE_SIZE) {
                return;
            }
            pageNo++;
        }
    }
    
    private void backfillAiResource(String namespaceId, AiResource resource,
        BackfillStats stats) {
        stats.scanned++;
        try {
            if (!needsAiResourceRebuild(namespaceId, resource)) {
                stats.skipped++;
                return;
            }
            indexBuildService.rebuildLatestAiResource(namespaceId, resource.getType(),
                resource.getName());
            stats.rebuilt++;
        } catch (Exception e) {
            stats.failed++;
            LOGGER.warn("Failed to backfill ARD index for {}:{} in namespace {}",
                resource.getType(), resource.getName(), namespaceId, e);
        }
    }
    
    private boolean needsAiResourceRebuild(String namespaceId, AiResource resource) {
        ArdEntry current =
            repository.findEntry(namespaceId, resource.getType(), resource.getName());
        String latestVersion = AiResourceManager.resolveVersion(resource, null,
            AiResourceConstants.LABEL_LATEST);
        if (StringUtils.isBlank(latestVersion)) {
            return current != null;
        }
        AiResourceVersion version = resourceManager.findVersion(namespaceId, resource.getName(),
            resource.getType(), latestVersion);
        if (!isIndexable(resource, version)) {
            return current != null;
        }
        ArdEntry expected = entryBuilder.fromAiResource(resource, version);
        return !isCurrent(current, expected);
    }
    
    private void backfillMcpServers(String namespaceId, BackfillStats stats) {
        int pageNo = 1;
        while (true) {
            Page<McpServerBasicInfo> page = mcpServerOperationService.listMcpServerWithPage(
                namespaceId, null, Constants.MCP_LIST_SEARCH_ACCURATE, pageNo, SCAN_PAGE_SIZE);
            List<McpServerBasicInfo> servers = page == null ? null : page.getPageItems();
            if (servers == null || servers.isEmpty()) {
                return;
            }
            for (McpServerBasicInfo server : servers) {
                backfillMcpServer(namespaceId, server, stats);
            }
            if (servers.size() < SCAN_PAGE_SIZE) {
                return;
            }
            pageNo++;
        }
    }
    
    private void backfillMcpServer(String namespaceId, McpServerBasicInfo server,
        BackfillStats stats) {
        stats.scanned++;
        try {
            if (!needsMcpServerRebuild(namespaceId, server)) {
                stats.skipped++;
                return;
            }
            McpServerBasicInfo indexSource = server;
            if (isIndexable(server)) {
                indexSource = loadMcpServerDetail(namespaceId, server);
            }
            indexBuildService.rebuildMcpServer(namespaceId, indexSource);
            stats.rebuilt++;
        } catch (Exception e) {
            stats.failed++;
            LOGGER.warn("Failed to backfill ARD index for mcp:{} in namespace {}",
                firstNotBlank(server.getId(), server.getName()), namespaceId, e);
        }
    }
    
    private boolean needsMcpServerRebuild(String namespaceId, McpServerBasicInfo server) {
        String resourceName = firstNotBlank(server.getId(), server.getName());
        ArdEntry current = StringUtils.isBlank(resourceName) ? null
            : repository.findEntry(namespaceId, RESOURCE_TYPE_MCP, resourceName);
        if (!isIndexable(server) || StringUtils.isBlank(resourceName)) {
            return current != null;
        }
        ArdEntry expected = entryBuilder.fromMcpServer(namespaceId, server);
        return !isCurrent(current, expected);
    }
    
    private McpServerDetailInfo loadMcpServerDetail(String namespaceId,
        McpServerBasicInfo server) throws Exception {
        return mcpServerOperationService.getMcpServerDetail(namespaceId, server.getId(), null,
            resolveMcpVersion(server));
    }
    
    private boolean isCurrent(ArdEntry current, ArdEntry expected) {
        return current != null
            && Objects.equals(current.getResourceVersion(), expected.getResourceVersion())
            && Objects.equals(current.getSourceDigest(), expected.getSourceDigest());
    }
    
    private boolean isIndexable(AiResource resource, AiResourceVersion version) {
        return resource != null && version != null
            && AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(resource.getStatus())
            && AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(version.getStatus());
    }
    
    private boolean isIndexable(McpServerBasicInfo server) {
        return server != null && server.isEnabled()
            && AiConstants.Mcp.MCP_STATUS_ACTIVE.equalsIgnoreCase(server.getStatus())
            && StringUtils.isNotBlank(resolveMcpVersion(server));
    }
    
    private String resolveMcpVersion(McpServerBasicInfo server) {
        if (server.getVersionDetail() != null
            && StringUtils.isNotBlank(server.getVersionDetail().getVersion())) {
            return server.getVersionDetail().getVersion();
        }
        return server.getVersion();
    }
    
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
    
    private boolean tryAcquireBackfillMarker() {
        for (int i = 0; i < 2; i++) {
            try {
                ConfigForm form = new ConfigForm();
                form.setNamespaceId(
                    com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
                form.setGroup(BACKFILL_MARKER_GROUP);
                form.setDataId(BACKFILL_MARKER_DATA_ID);
                form.setContent(String.valueOf(System.currentTimeMillis()));
                form.setSrcUser("nacos");
                ConfigRequestInfo requestInfo = new ConfigRequestInfo();
                requestInfo.setUpdateForExist(false);
                configOperationService.publishConfig(form, requestInfo, null);
                return true;
            } catch (ConfigAlreadyExistsException e) {
                if (isBackfillMarkerStale()) {
                    LOGGER.warn("Found stale ARD index backfill marker, removing and retrying");
                    releaseBackfillMarker();
                    continue;
                }
                return false;
            } catch (Exception e) {
                LOGGER.error("Failed to create ARD index backfill marker", e);
                return false;
            }
        }
        return false;
    }
    
    private boolean isBackfillMarkerStale() {
        try {
            ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                BACKFILL_MARKER_DATA_ID, BACKFILL_MARKER_GROUP,
                com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND
                || StringUtils.isBlank(response.getContent())) {
                return false;
            }
            long markerTime = Long.parseLong(response.getContent().trim());
            return System.currentTimeMillis() - markerTime > BACKFILL_MARKER_STALE_MILLIS;
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect ARD index backfill marker", e);
            return false;
        }
    }
    
    private void releaseBackfillMarker() {
        try {
            configOperationService.deleteConfig(BACKFILL_MARKER_DATA_ID, BACKFILL_MARKER_GROUP,
                com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID, null, null, "nacos",
                null);
        } catch (Exception e) {
            LOGGER.warn("Failed to delete ARD index backfill marker", e);
        }
    }
    
    private static final class BackfillStats {
        
        private int scanned;
        
        private int rebuilt;
        
        private int skipped;
        
        private int failed;
    }
}
