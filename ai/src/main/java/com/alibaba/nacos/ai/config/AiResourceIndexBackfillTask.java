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
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.search.AiResourceEmbeddingService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchDocumentBuilder;
import com.alibaba.nacos.ai.service.search.AiResourceSearchConstants;
import com.alibaba.nacos.ai.service.search.AiResourceSearchRepository;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
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
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reconciles AI resource indexes for resources that existed before AI resource indexing was enabled.
 *
 * @author nacos
 */
@Component
@ConditionalOnAiResourceSearchEnabled
public class AiResourceIndexBackfillTask
    implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {
    
    static final String BACKFILL_ENABLED_KEY = "nacos.ai.resource.search.index.backfill.enabled";
    
    static final String RECONCILE_INTERVAL_SECONDS_KEY =
        "nacos.ai.resource.search.index.reconcile.interval-seconds";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiResourceIndexBackfillTask.class);
    
    private static final String BACKFILL_MARKER_DATA_ID = "nacos.ai.resource.search.index.backfill";
    
    private static final String BACKFILL_MARKER_GROUP = "nacos_internal";
    
    private static final long BACKFILL_MARKER_STALE_MILLIS = 10 * 60 * 1000L;
    
    private static final long BACKFILL_MARKER_RENEW_MILLIS =
        BACKFILL_MARKER_STALE_MILLIS / 3;
    
    private static final int SCAN_PAGE_SIZE = 100;
    
    private static final long DEFAULT_RECONCILE_INTERVAL_SECONDS = 300L;
    
    private static final String RESOURCE_TYPE_MCP = "mcp";
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private final ScheduledExecutorService backfillExecutor =
        ExecutorFactory.Managed.newSingleScheduledExecutorService(
            AiResourceIndexBackfillTask.class.getCanonicalName() + ".scan",
            new ThreadFactoryBuilder().daemon(true)
                .nameFormat("nacos-ai-resource-index-backfill-%d").build());
    
    private final ScheduledExecutorService markerLeaseExecutor =
        ExecutorFactory.Managed.newSingleScheduledExecutorService(
            AiResourceIndexBackfillTask.class.getCanonicalName() + ".lease",
            new ThreadFactoryBuilder().daemon(true)
                .nameFormat("nacos-ai-resource-index-reconcile-lease-%d").build());
    
    private final AiResourceManager resourceManager;
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final AiResourceSearchRepository repository;
    
    private final AiResourceIndexMaintenanceService indexMaintenanceService;
    
    private final AiResourceEmbeddingService embeddingService;
    
    private final AiResourceVectorIndex vectorIndex;
    
    private final NamespaceOperationService namespaceOperationService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final AiResourceSearchDocumentBuilder entryBuilder =
        new AiResourceSearchDocumentBuilder();
    
    public AiResourceIndexBackfillTask(AiResourceManager resourceManager,
        McpServerOperationService mcpServerOperationService, AiResourceSearchRepository repository,
        AiResourceIndexMaintenanceService indexMaintenanceService,
        AiResourceEmbeddingService embeddingService, AiResourceVectorIndex vectorIndex,
        NamespaceOperationService namespaceOperationService,
        ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService) {
        this.resourceManager = resourceManager;
        this.mcpServerOperationService = mcpServerOperationService;
        this.repository = repository;
        this.indexMaintenanceService = indexMaintenanceService;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
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
            LOGGER.info("AI resource index backfill is disabled via {}", BACKFILL_ENABLED_KEY);
            return;
        }
        long intervalSeconds = positiveLong(RECONCILE_INTERVAL_SECONDS_KEY,
            DEFAULT_RECONCILE_INTERVAL_SECONDS);
        backfillExecutor.scheduleWithFixedDelay(this::executeBackfill, 0L, intervalSeconds,
            TimeUnit.SECONDS);
    }
    
    private void executeBackfill() {
        MarkerLease marker = null;
        try {
            marker = tryAcquireBackfillMarker();
            if (marker == null) {
                LOGGER
                    .info("Skip AI resource index backfill because another node is processing it");
                return;
            }
            BackfillStats stats = new BackfillStats();
            ReconciliationContext context = reconciliationContext();
            for (Namespace namespace : getNamespaces()) {
                marker.assertOwned();
                String namespaceId = namespace.getNamespace();
                reconcileAiResources(namespaceId, Constants.Skills.RESOURCE_TYPE_SKILL, context,
                    marker, stats);
                reconcileAiResources(namespaceId,
                    NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, context, marker, stats);
                reconcileMcpServers(namespaceId, context, marker, stats);
            }
            LOGGER.info(
                "AI resource index backfill completed: scanned={}, rebuilt={}, skipped={}, failed={}",
                stats.scanned, stats.rebuilt, stats.skipped, stats.failed);
        } catch (Exception e) {
            LOGGER.error("AI resource index backfill failed unexpectedly", e);
        } finally {
            if (marker != null) {
                marker.close();
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
            LOGGER.warn("Failed to list namespaces for AI resource index backfill", e);
        }
        return Collections.singletonList(new Namespace(
            com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID, "public"));
    }
    
    private void reconcileAiResources(String namespaceId, String resourceType,
        ReconciliationContext context, MarkerLease marker, BackfillStats stats) {
        int pageNo = 1;
        while (true) {
            marker.assertOwned();
            Page<AiResource> page = resourceManager.listMetaByType(namespaceId, resourceType, null,
                null, pageNo, SCAN_PAGE_SIZE);
            List<AiResource> resources = page == null ? null : page.getPageItems();
            if (resources == null || resources.isEmpty()) {
                break;
            }
            for (AiResource resource : resources) {
                backfillAiResource(namespaceId, resource, context, stats);
            }
            if (resources.size() < SCAN_PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
        scheduleOrphanDeletes(namespaceId, resourceType, marker, stats);
    }
    
    private void backfillAiResource(String namespaceId, AiResource resource,
        ReconciliationContext context, BackfillStats stats) {
        stats.scanned++;
        try {
            if (!needsAiResourceRebuild(namespaceId, resource, context)) {
                stats.skipped++;
                return;
            }
            if (indexMaintenanceService.scheduleReconciliation(namespaceId, resource.getType(),
                resource.getName())) {
                stats.rebuilt++;
            } else {
                stats.failed++;
            }
        } catch (Exception e) {
            stats.failed++;
            LOGGER.warn("Failed to backfill AI resource index for {}:{} in namespace {}",
                resource.getType(), resource.getName(), namespaceId, e);
        }
    }
    
    private boolean needsAiResourceRebuild(String namespaceId, AiResource resource,
        ReconciliationContext context) {
        AiResourceSearchDocument current =
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
        AiResourceSearchDocument expected = entryBuilder.fromAiResource(resource, version);
        return !isCurrent(current, expected, context);
    }
    
    private void reconcileMcpServers(String namespaceId, ReconciliationContext context,
        MarkerLease marker, BackfillStats stats) {
        int pageNo = 1;
        while (true) {
            marker.assertOwned();
            Page<McpServerBasicInfo> page = mcpServerOperationService.listMcpServerWithPage(
                namespaceId, null, Constants.MCP_LIST_SEARCH_ACCURATE, pageNo, SCAN_PAGE_SIZE);
            List<McpServerBasicInfo> servers = page == null ? null : page.getPageItems();
            if (servers == null || servers.isEmpty()) {
                break;
            }
            for (McpServerBasicInfo server : servers) {
                backfillMcpServer(namespaceId, server, context, stats);
            }
            if (servers.size() < SCAN_PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
        scheduleOrphanDeletes(namespaceId, RESOURCE_TYPE_MCP, marker, stats);
    }
    
    private void backfillMcpServer(String namespaceId, McpServerBasicInfo server,
        ReconciliationContext context, BackfillStats stats) {
        stats.scanned++;
        try {
            if (!needsMcpServerRebuild(namespaceId, server, context)) {
                stats.skipped++;
                return;
            }
            String resourceName = firstNotBlank(server.getId(), server.getName());
            if (indexMaintenanceService.scheduleReconciliation(namespaceId, RESOURCE_TYPE_MCP,
                resourceName)) {
                stats.rebuilt++;
            } else {
                stats.failed++;
            }
        } catch (Exception e) {
            stats.failed++;
            LOGGER.warn("Failed to backfill AI resource index for mcp:{} in namespace {}",
                firstNotBlank(server.getId(), server.getName()), namespaceId, e);
        }
    }
    
    private boolean needsMcpServerRebuild(String namespaceId, McpServerBasicInfo server,
        ReconciliationContext context) {
        String resourceName = firstNotBlank(server.getId(), server.getName());
        AiResourceSearchDocument current = StringUtils.isBlank(resourceName) ? null
            : repository.findEntry(namespaceId, RESOURCE_TYPE_MCP, resourceName);
        if (!isIndexable(server) || StringUtils.isBlank(resourceName)) {
            return current != null;
        }
        AiResourceSearchDocument expected = entryBuilder.fromMcpServer(namespaceId, server);
        return !isCurrent(current, expected, context);
    }
    
    private boolean isCurrent(AiResourceSearchDocument current,
        AiResourceSearchDocument expected, ReconciliationContext context) {
        if (current == null
            || !AiResourceSearchConstants.STATUS_ENABLED.equals(current.getStatus())) {
            return false;
        }
        if (!Objects.equals(current.getResourceVersion(), expected.getResourceVersion())
            || !Objects.equals(current.getSourceDigest(), expected.getSourceDigest())) {
            return false;
        }
        if (!context.vectorAvailable) {
            return true;
        }
        return current.getId() != null && vectorIndex.isResourceVersionReady(
            current.getNamespaceId(), current.getResourceType(), current.getResourceName(),
            current.getResourceVersion(), context.embeddingModel, current.getId(),
            repository.countChunks(current.getId()));
    }
    
    private void scheduleOrphanDeletes(String namespaceId, String resourceType,
        MarkerLease marker, BackfillStats stats) {
        long afterId = 0L;
        while (true) {
            marker.assertOwned();
            List<AiResourceSearchDocument> batch = repository.scanEntries(namespaceId,
                Collections.singletonList(resourceType), afterId, SCAN_PAGE_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (AiResourceSearchDocument entry : batch) {
                if (canonicalResourceExists(namespaceId, resourceType,
                    entry.getResourceName())) {
                    continue;
                }
                if (indexMaintenanceService.scheduleReconciliation(namespaceId, resourceType,
                    entry.getResourceName())) {
                    stats.rebuilt++;
                } else {
                    stats.failed++;
                }
            }
            AiResourceSearchDocument last = batch.get(batch.size() - 1);
            if (last.getId() == null || last.getId() <= afterId) {
                throw new IllegalStateException("AI resource index reconciliation did not advance");
            }
            afterId = last.getId();
            if (batch.size() < SCAN_PAGE_SIZE) {
                break;
            }
        }
    }
    
    private boolean canonicalResourceExists(String namespaceId, String resourceType,
        String resourceName) {
        if (!RESOURCE_TYPE_MCP.equals(resourceType)) {
            return resourceManager.findMeta(namespaceId, resourceName, resourceType) != null;
        }
        try {
            return mcpServerOperationService.getMcpServerDetail(namespaceId, resourceName, null,
                null) != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    private ReconciliationContext reconciliationContext() {
        boolean vectorAvailable = vectorIndex.available();
        return new ReconciliationContext(vectorAvailable,
            vectorAvailable ? embeddingService.model() : null);
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
    
    private long positiveLong(String key, long defaultValue) {
        try {
            long value = Long.parseLong(EnvUtil.getProperty(key, String.valueOf(defaultValue)));
            return value > 0 ? value : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
    
    private MarkerLease tryAcquireBackfillMarker() {
        String owner = UUID.randomUUID().toString();
        try {
            publishMarker(markerContent(owner), false, null);
            return new MarkerLease(owner);
        } catch (ConfigAlreadyExistsException e) {
            MarkerRecord current = readMarker();
            if (current == null || !current.expired()) {
                return null;
            }
            if (StringUtils.isBlank(current.md5)) {
                LOGGER.warn(
                    "Cannot take over expired AI resource index reconciliation lease "
                        + "without CAS metadata");
                return null;
            }
            try {
                publishMarker(markerContent(owner), true, current.md5);
                LOGGER.warn("Took over expired AI resource index reconciliation lease");
                return new MarkerLease(owner);
            } catch (Exception takeoverFailure) {
                LOGGER.info("AI resource index reconciliation lease was taken by another node");
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create AI resource index backfill marker", e);
            return null;
        }
    }
    
    private MarkerRecord readMarker() {
        try {
            ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                BACKFILL_MARKER_DATA_ID, BACKFILL_MARKER_GROUP,
                com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response == null
                || response
                    .getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND
                || StringUtils.isBlank(response.getContent())) {
                return null;
            }
            return MarkerRecord.parse(response.getContent(), response.getMd5());
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect AI resource index backfill marker", e);
            return null;
        }
    }
    
    private String markerContent(String owner) {
        return owner + "|" + (System.currentTimeMillis() + BACKFILL_MARKER_STALE_MILLIS);
    }
    
    private void publishMarker(String content, boolean updateForExist, String casMd5)
        throws Exception {
        ConfigForm form = new ConfigForm();
        form.setNamespaceId(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
        form.setGroup(BACKFILL_MARKER_GROUP);
        form.setDataId(BACKFILL_MARKER_DATA_ID);
        form.setContent(content);
        form.setSrcUser("nacos");
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        requestInfo.setUpdateForExist(updateForExist);
        requestInfo.setCasMd5(casMd5);
        configOperationService.publishConfig(form, requestInfo, null);
    }
    
    @Override
    public void destroy() {
        backfillExecutor.shutdownNow();
        markerLeaseExecutor.shutdownNow();
    }
    
    private static final class BackfillStats {
        
        private int scanned;
        
        private int rebuilt;
        
        private int skipped;
        
        private int failed;
    }
    
    private static final class ReconciliationContext {
        
        private final boolean vectorAvailable;
        
        private final String embeddingModel;
        
        private ReconciliationContext(boolean vectorAvailable, String embeddingModel) {
            this.vectorAvailable = vectorAvailable;
            this.embeddingModel = embeddingModel;
        }
    }
    
    private final class MarkerLease implements AutoCloseable {
        
        private final String owner;
        
        private final AtomicBoolean owned = new AtomicBoolean(true);
        
        private final ScheduledFuture<?> renewal;
        
        private MarkerLease(String owner) {
            this.owner = owner;
            this.renewal = markerLeaseExecutor.scheduleWithFixedDelay(this::renewSafely,
                BACKFILL_MARKER_RENEW_MILLIS, BACKFILL_MARKER_RENEW_MILLIS,
                TimeUnit.MILLISECONDS);
        }
        
        private void assertOwned() {
            if (!owned.get()) {
                throw new IllegalStateException(
                    "AI resource index reconciliation lease was lost");
            }
        }
        
        private void renewSafely() {
            try {
                MarkerRecord current = readMarker();
                if (current == null || !owner.equals(current.owner)
                    || StringUtils.isBlank(current.md5)) {
                    owned.set(false);
                    return;
                }
                publishMarker(markerContent(owner), true, current.md5);
            } catch (Exception e) {
                owned.set(false);
                LOGGER.warn("Failed to renew AI resource index reconciliation lease", e);
            }
        }
        
        @Override
        public void close() {
            renewal.cancel(false);
            if (!owned.get()) {
                return;
            }
            try {
                MarkerRecord current = readMarker();
                if (current != null && owner.equals(current.owner)
                    && StringUtils.isNotBlank(current.md5)) {
                    publishMarker(owner + "|0", true, current.md5);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to release AI resource index reconciliation lease", e);
            }
        }
    }
    
    private static final class MarkerRecord {
        
        private final String owner;
        
        private final long expireAt;
        
        private final String md5;
        
        private MarkerRecord(String owner, long expireAt, String md5) {
            this.owner = owner;
            this.expireAt = expireAt;
            this.md5 = md5;
        }
        
        private boolean expired() {
            return expireAt <= System.currentTimeMillis();
        }
        
        private static MarkerRecord parse(String content, String md5) {
            String value = content.trim();
            int separator = value.lastIndexOf('|');
            if (separator < 0) {
                long createdAt = Long.parseLong(value);
                return new MarkerRecord("", createdAt + BACKFILL_MARKER_STALE_MILLIS, md5);
            }
            return new MarkerRecord(value.substring(0, separator),
                Long.parseLong(value.substring(separator + 1)), md5);
        }
    }
}
