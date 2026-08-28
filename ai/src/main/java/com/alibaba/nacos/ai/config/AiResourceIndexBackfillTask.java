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

import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.mcp.McpOperationService;
import com.alibaba.nacos.ai.service.search.AiResourceEmbeddingService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexContentLoader;
import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexProjection;
import com.alibaba.nacos.ai.service.search.AiResourceIndexSource;
import com.alibaba.nacos.ai.service.search.AiResourceIndexSourcePage;
import com.alibaba.nacos.ai.service.search.AiResourceSearchTypeHandler;
import com.alibaba.nacos.ai.service.search.AiResourceSearchTypeHandlerRegistry;
import com.alibaba.nacos.ai.service.search.AiResourceSearchConstants;
import com.alibaba.nacos.ai.service.search.AiResourceSearchRepository;
import com.alibaba.nacos.ai.service.search.AiResourceSearchReadinessService;
import com.alibaba.nacos.ai.service.search.McpAiResourceSearchTypeHandler;
import com.alibaba.nacos.ai.service.search.StoredAiResourceSearchTypeHandler;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.MD5Utils;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    
    private final AiResourceSearchTypeHandlerRegistry typeHandlerRegistry;
    
    private final AiResourceSearchRepository repository;
    
    private final AiResourceIndexMaintenanceService indexMaintenanceService;
    
    private final AiResourceEmbeddingService embeddingService;
    
    private final AiResourceVectorIndex vectorIndex;
    
    private final NamespaceOperationService namespaceOperationService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final AiResourceSearchReadinessService readinessService;
    
    @Autowired
    public AiResourceIndexBackfillTask(AiResourceSearchTypeHandlerRegistry typeHandlerRegistry,
        AiResourceSearchRepository repository,
        AiResourceIndexMaintenanceService indexMaintenanceService,
        AiResourceEmbeddingService embeddingService, AiResourceVectorIndex vectorIndex,
        NamespaceOperationService namespaceOperationService,
        ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService,
        AiResourceSearchReadinessService readinessService) {
        this.typeHandlerRegistry = typeHandlerRegistry;
        this.repository = repository;
        this.indexMaintenanceService = indexMaintenanceService;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
        this.namespaceOperationService = namespaceOperationService;
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.readinessService = readinessService;
    }
    
    AiResourceIndexBackfillTask(AiResourceManager resourceManager,
        McpOperationService mcpOperationService,
        AiResourceSearchRepository repository,
        AiResourceIndexMaintenanceService indexMaintenanceService,
        AiResourceEmbeddingService embeddingService, AiResourceVectorIndex vectorIndex,
        NamespaceOperationService namespaceOperationService,
        ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService) {
        this(new AiResourceSearchTypeHandlerRegistry(List.of(
            new StoredAiResourceSearchTypeHandler(resourceManager,
                AiResourceIndexContentLoader.NOOP),
            new McpAiResourceSearchTypeHandler(mcpOperationService))), repository,
            indexMaintenanceService, embeddingService, vectorIndex, namespaceOperationService,
            configQueryChainService, configOperationService,
            AiResourceSearchReadinessService.NOOP);
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
            Map<String, ReadinessTarget> readinessTargets = readinessTargets();
            ReconciliationContext context = reconciliationContext();
            NamespaceScan namespaceScan = getNamespaces();
            for (Namespace namespace : namespaceScan.namespaces) {
                marker.assertOwned();
                String namespaceId = namespace.getNamespace();
                for (AiResourceSearchTypeHandler handler : typeHandlerRegistry.handlers()) {
                    for (String resourceType : handler.resourceTypes()) {
                        BackfillStats typeStats = reconcileResources(namespaceId, resourceType,
                            handler, context, marker);
                        stats.add(typeStats);
                        ReadinessTarget target = readinessTargets.get(resourceType);
                        if (target != null) {
                            target.stats.add(typeStats);
                        }
                    }
                }
            }
            marker.assertOwned();
            if (namespaceScan.complete) {
                recordReadiness(readinessTargets);
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
    
    private NamespaceScan getNamespaces() {
        try {
            List<Namespace> namespaces = namespaceOperationService.getNamespaceList();
            if (namespaces != null && !namespaces.isEmpty()) {
                return new NamespaceScan(namespaces, true);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to list namespaces for AI resource index backfill", e);
        }
        return new NamespaceScan(Collections.singletonList(new Namespace(
            com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID, "public")), false);
    }
    
    private BackfillStats reconcileResources(String namespaceId, String resourceType,
        AiResourceSearchTypeHandler handler, ReconciliationContext context, MarkerLease marker)
        throws Exception {
        BackfillStats stats = new BackfillStats();
        int pageNo = 1;
        while (true) {
            marker.assertOwned();
            AiResourceIndexSourcePage page =
                handler.scan(namespaceId, resourceType, pageNo, SCAN_PAGE_SIZE);
            if (page.getItems().isEmpty()) {
                break;
            }
            for (AiResourceIndexSource source : page.getItems()) {
                backfillResource(namespaceId, resourceType, source, context, stats);
            }
            if (!page.hasMore()) {
                break;
            }
            pageNo++;
        }
        scheduleOrphanDeletes(namespaceId, resourceType, handler, marker, stats);
        return stats;
    }
    
    private Map<String, ReadinessTarget> readinessTargets() {
        Map<String, ReadinessTarget> result = new LinkedHashMap<>();
        for (AiResourceSearchTypeHandler handler : typeHandlerRegistry.handlers()) {
            if (handler.projectionVersion() <= 0) {
                continue;
            }
            for (String resourceType : handler.resourceTypes()) {
                result.put(resourceType,
                    new ReadinessTarget(resourceType, handler.projectionVersion()));
            }
        }
        return result;
    }
    
    private void recordReadiness(Map<String, ReadinessTarget> targets) {
        for (ReadinessTarget target : targets.values()) {
            readinessService.recordCompletedScan(target.resourceType, target.projectionVersion,
                target.stats.rebuilt == 0 && target.stats.failed == 0);
        }
    }
    
    private void backfillResource(String namespaceId, String resourceType,
        AiResourceIndexSource source, ReconciliationContext context, BackfillStats stats) {
        stats.scanned++;
        if (source.getFailure() != null) {
            stats.failed++;
            LOGGER.warn("Failed to project AI resource index for {}:{} in namespace {}",
                resourceType, source.getResourceName(), namespaceId, source.getFailure());
            return;
        }
        try {
            if (!needsRebuild(namespaceId, resourceType, source, context)) {
                stats.skipped++;
                return;
            }
            if (indexMaintenanceService.scheduleReconciliation(namespaceId, resourceType,
                source.getResourceName())) {
                stats.rebuilt++;
            } else {
                stats.failed++;
            }
        } catch (Exception e) {
            stats.failed++;
            LOGGER.warn("Failed to backfill AI resource index for {}:{} in namespace {}",
                resourceType, source.getResourceName(), namespaceId, e);
        }
    }
    
    private boolean needsRebuild(String namespaceId, String resourceType,
        AiResourceIndexSource source,
        ReconciliationContext context) {
        AiResourceSearchDocument current = StringUtils.isBlank(source.getResourceName()) ? null
            : repository.findEntry(namespaceId, resourceType, source.getResourceName());
        AiResourceIndexProjection projection = source.getProjection();
        if (projection == null) {
            return current != null;
        }
        return !isCurrent(current, projection.getDocument(), context);
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
        AiResourceSearchTypeHandler handler, MarkerLease marker, BackfillStats stats) {
        long afterId = 0L;
        while (true) {
            marker.assertOwned();
            List<AiResourceSearchDocument> batch = repository.scanEntries(namespaceId,
                Collections.singletonList(resourceType), afterId, SCAN_PAGE_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (AiResourceSearchDocument entry : batch) {
                try {
                    if (handler.exists(namespaceId, resourceType, entry.getResourceName())) {
                        continue;
                    }
                } catch (Exception e) {
                    stats.failed++;
                    LOGGER.warn("Failed to check canonical AI resource {}:{} in namespace {}",
                        resourceType, entry.getResourceName(), namespaceId, e);
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
    
    private ReconciliationContext reconciliationContext() {
        boolean vectorAvailable = vectorIndex.available();
        return new ReconciliationContext(vectorAvailable,
            vectorAvailable ? embeddingService.model() : null);
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
        String content = markerContent(owner);
        try {
            publishMarker(content, false, null);
            return new MarkerLease(owner, markerMd5(content));
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
                publishMarker(content, true, current.md5);
                LOGGER.warn("Took over expired AI resource index reconciliation lease");
                return new MarkerLease(owner, markerMd5(content));
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
    
    private String markerMd5(String content) {
        return MD5Utils.md5Hex(content,
            com.alibaba.nacos.api.common.Constants.ENCODE);
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
        
        private void add(BackfillStats source) {
            scanned += source.scanned;
            rebuilt += source.rebuilt;
            skipped += source.skipped;
            failed += source.failed;
        }
    }
    
    private static final class ReadinessTarget {
        
        private final String resourceType;
        
        private final int projectionVersion;
        
        private final BackfillStats stats = new BackfillStats();
        
        private ReadinessTarget(String resourceType, int projectionVersion) {
            this.resourceType = resourceType;
            this.projectionVersion = projectionVersion;
        }
    }
    
    private static final class NamespaceScan {
        
        private final List<Namespace> namespaces;
        
        private final boolean complete;
        
        private NamespaceScan(List<Namespace> namespaces, boolean complete) {
            this.namespaces = namespaces;
            this.complete = complete;
        }
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
        
        private String currentMd5;
        
        private MarkerLease(String owner, String markerMd5) {
            this.owner = owner;
            this.currentMd5 = markerMd5;
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
        
        private synchronized void renewSafely() {
            if (!owned.get()) {
                return;
            }
            try {
                String content = markerContent(owner);
                publishMarker(content, true, currentMd5);
                currentMd5 = markerMd5(content);
            } catch (Exception e) {
                owned.set(false);
                LOGGER.warn("Failed to renew AI resource index reconciliation lease", e);
            }
        }
        
        @Override
        public synchronized void close() {
            renewal.cancel(false);
            if (!owned.compareAndSet(true, false)) {
                return;
            }
            try {
                publishMarker(owner + "|0", true, currentMd5);
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
