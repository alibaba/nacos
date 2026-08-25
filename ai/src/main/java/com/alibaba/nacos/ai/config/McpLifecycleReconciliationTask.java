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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.mcp.McpHistoricalResourceReconciler;
import com.alibaba.nacos.ai.service.mcp.storage.McpServingManifestStorage;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.JacksonUtils;
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
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically reconciles historical MCP serving data into AI Resource lifecycle rows.
 *
 * <p>The task remains in {@code SYNCING}. It persists diagnostic progress and uses a renewable
 * Config lease, but deliberately does not delete lifecycle rows, write the permanent
 * {@code LIFECYCLE_MANAGED} marker, or switch any management route.</p>
 *
 * @author Nacos
 */
@Component
public class McpLifecycleReconciliationTask
    implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {
    
    static final String RECONCILIATION_ENABLED_KEY =
        "nacos.ai.mcp.resource.reconciliation.enabled";
    
    static final String RECONCILIATION_INTERVAL_SECONDS_KEY =
        "nacos.ai.mcp.resource.reconciliation.interval-seconds";
    
    static final String RECONCILIATION_LEASE_DATA_ID =
        "nacos.ai.mcp.resource.reconciliation.lease.v1";
    
    static final String RECONCILIATION_PROGRESS_DATA_ID =
        "nacos.ai.mcp.resource.reconciliation.progress.v1";
    
    static final String INTERNAL_GROUP = "nacos_internal";
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(McpLifecycleReconciliationTask.class);
    
    private static final int SCAN_PAGE_SIZE = 100;
    
    private static final long DEFAULT_RECONCILIATION_INTERVAL_SECONDS = 300L;
    
    private static final long LEASE_DURATION_MILLIS = 10 * 60 * 1000L;
    
    private static final long LEASE_RENEW_MILLIS = LEASE_DURATION_MILLIS / 3;
    
    private static final int MAX_LAST_ERROR_LENGTH = 2048;
    
    private static final String STATE_SYNCING = "SYNCING";
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private final ScheduledExecutorService reconciliationExecutor =
        ExecutorFactory.Managed.newSingleScheduledExecutorService(
            McpLifecycleReconciliationTask.class.getCanonicalName() + ".scan",
            new ThreadFactoryBuilder().daemon(true)
                .nameFormat("nacos-ai-mcp-resource-reconcile-%d").build());
    
    private final ScheduledExecutorService leaseExecutor =
        ExecutorFactory.Managed.newSingleScheduledExecutorService(
            McpLifecycleReconciliationTask.class.getCanonicalName() + ".lease",
            new ThreadFactoryBuilder().daemon(true)
                .nameFormat("nacos-ai-mcp-resource-reconcile-lease-%d").build());
    
    private final NamespaceOperationService namespaceOperationService;
    
    private final McpServingManifestStorage manifestStorage;
    
    private final McpHistoricalResourceReconciler reconciler;
    
    private final AiResourcePersistService resourcePersistService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    public McpLifecycleReconciliationTask(NamespaceOperationService namespaceOperationService,
        McpServingManifestStorage manifestStorage,
        McpHistoricalResourceReconciler reconciler,
        AiResourcePersistService resourcePersistService,
        ConfigQueryChainService configQueryChainService,
        ConfigOperationService configOperationService) {
        this.namespaceOperationService = namespaceOperationService;
        this.manifestStorage = manifestStorage;
        this.reconciler = reconciler;
        this.resourcePersistService = resourcePersistService;
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
        if (!Boolean.parseBoolean(EnvUtil.getProperty(RECONCILIATION_ENABLED_KEY, "true"))) {
            LOGGER.info("MCP lifecycle reconciliation is disabled via {}",
                RECONCILIATION_ENABLED_KEY);
            return;
        }
        long intervalSeconds = positiveLong(RECONCILIATION_INTERVAL_SECONDS_KEY,
            DEFAULT_RECONCILIATION_INTERVAL_SECONDS);
        reconciliationExecutor.scheduleWithFixedDelay(this::executeReconciliation, 0L,
            intervalSeconds, TimeUnit.SECONDS);
    }
    
    void executeReconciliation() {
        ReconciliationLease lease = tryAcquireLease();
        if (lease == null) {
            LOGGER.debug("Skip MCP lifecycle reconciliation because another node owns the lease");
            return;
        }
        ReconciliationStats stats = new ReconciliationStats();
        stats.generation = UUID.randomUUID().toString();
        stats.startedAt = System.currentTimeMillis();
        try {
            NamespaceScan namespaceScan = getNamespaces();
            stats.complete = namespaceScan.complete;
            if (!namespaceScan.complete) {
                stats.recordFailure("Namespace listing was incomplete");
            }
            for (String namespaceId : namespaceScan.namespaceIds) {
                lease.assertOwned();
                reconcileNamespace(namespaceId, lease, stats);
                stats.namespaces++;
            }
            lease.assertOwned();
        } catch (Exception e) {
            stats.recordFailure(e.getMessage());
            LOGGER.error("MCP lifecycle reconciliation failed unexpectedly", e);
        } finally {
            stats.completedAt = System.currentTimeMillis();
            if (lease.isOwned()) {
                persistProgress(stats);
            }
            lease.close();
            LOGGER.info(
                "MCP lifecycle reconciliation completed: generation={}, namespaces={}, "
                    + "manifests={}, changed={}, orphaned={}, failed={}, zeroDifference={}",
                stats.generation, stats.namespaces, stats.manifests, stats.changed,
                stats.orphaned, stats.failed, stats.zeroDifference());
        }
    }
    
    private void reconcileNamespace(String namespaceId, ReconciliationLease lease,
        ReconciliationStats stats) throws Exception {
        Set<String> manifestNames = new LinkedHashSet<>();
        Map<String, String> idsByName = new HashMap<>();
        Map<String, String> namesById = new HashMap<>();
        int pageNo = 1;
        int pagesAvailable = 1;
        while (pageNo <= pagesAvailable) {
            lease.assertOwned();
            Page<McpServerVersionInfo> page = manifestStorage.list(namespaceId, pageNo,
                SCAN_PAGE_SIZE);
            pagesAvailable = resolvePages(page, pageNo, SCAN_PAGE_SIZE);
            for (McpServerVersionInfo manifest : page.getPageItems()) {
                lease.assertOwned();
                stats.manifests++;
                manifestNames.add(manifest.getName());
                String previousId = idsByName.putIfAbsent(manifest.getName(), manifest.getId());
                String previousName = namesById.putIfAbsent(manifest.getId(), manifest.getName());
                if (previousId != null || previousName != null) {
                    stats.recordFailure("Duplicate historical MCP identity in namespace "
                        + namespaceId + ": " + manifest.getName() + '/' + manifest.getId());
                    continue;
                }
                try {
                    stats.changed += reconciler.reconcile(namespaceId, manifest);
                } catch (Exception e) {
                    stats.recordFailure("Failed to reconcile MCP " + manifest.getName()
                        + " in namespace " + namespaceId + ": " + e.getMessage());
                    LOGGER.warn("Failed to reconcile historical MCP {} in namespace {}",
                        manifest.getName(), namespaceId, e);
                }
            }
            pageNo++;
        }
        detectOrphans(namespaceId, manifestNames, lease, stats);
    }
    
    private void detectOrphans(String namespaceId, Set<String> manifestNames,
        ReconciliationLease lease, ReconciliationStats stats) {
        QueryCondition condition = new QueryCondition();
        condition.setNamespaceId(namespaceId);
        condition.setType(AiResourceConstants.RESOURCE_TYPE_MCP);
        int pageNo = 1;
        int pagesAvailable = 1;
        while (pageNo <= pagesAvailable) {
            lease.assertOwned();
            Page<AiResource> page = resourcePersistService.list(condition, pageNo,
                SCAN_PAGE_SIZE);
            if (page == null || page.getPageItems() == null) {
                throw new IllegalStateException(
                    "Unable to page MCP Resource rows for orphan detection");
            }
            pagesAvailable = resolvePages(page, pageNo, SCAN_PAGE_SIZE);
            collectOrphans(namespaceId, manifestNames, page.getPageItems(), stats);
            pageNo++;
        }
    }
    
    private void collectOrphans(String namespaceId, Set<String> manifestNames,
        Collection<AiResource> resources, ReconciliationStats stats) {
        for (AiResource resource : resources) {
            if (resource == null || !namespaceId.equals(resource.getNamespaceId())
                || !AiResourceConstants.RESOURCE_TYPE_MCP.equals(resource.getType())
                || StringUtils.isBlank(resource.getName())) {
                throw new IllegalStateException(
                    "MCP Resource scan returned an inconsistent row in namespace " + namespaceId);
            }
            if (McpHistoricalResourceReconciler.LEGACY_SOURCE.equals(resource.getFrom())
                && !manifestNames.contains(resource.getName())) {
                stats.orphaned++;
                stats.recordFailure("Historical MCP Resource has no serving Manifest: "
                    + namespaceId + '/' + resource.getName());
            }
        }
    }
    
    private NamespaceScan getNamespaces() {
        try {
            List<Namespace> namespaces = namespaceOperationService.getNamespaceList();
            if (namespaces != null && !namespaces.isEmpty()) {
                List<String> namespaceIds = namespaces.stream().filter(Objects::nonNull)
                    .map(Namespace::getNamespace).filter(StringUtils::isNotBlank).distinct()
                    .sorted(Comparator.naturalOrder()).toList();
                if (!namespaceIds.isEmpty()) {
                    return new NamespaceScan(namespaceIds, true);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to list namespaces for MCP lifecycle reconciliation", e);
        }
        List<String> fallback = new ArrayList<>(1);
        fallback.add(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
        return new NamespaceScan(fallback, false);
    }
    
    private int resolvePages(Page<?> page, int pageNo, int pageSize) {
        if (page == null || page.getPageItems() == null) {
            throw new IllegalStateException("MCP reconciliation received an empty page");
        }
        if (page.getPagesAvailable() > 0) {
            return Math.max(pageNo, page.getPagesAvailable());
        }
        int calculated = (page.getTotalCount() + pageSize - 1) / pageSize;
        return Math.max(pageNo, calculated);
    }
    
    private long positiveLong(String key, long defaultValue) {
        try {
            long value = Long.parseLong(EnvUtil.getProperty(key, String.valueOf(defaultValue)));
            return value > 0 ? value : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
    
    private ReconciliationLease tryAcquireLease() {
        String owner = UUID.randomUUID().toString();
        String content = leaseContent(owner);
        LeaseRecord current = readLease();
        if (current == null) {
            try {
                publishLease(content, false, null);
                return new ReconciliationLease(owner, contentMd5(content));
            } catch (ConfigAlreadyExistsException e) {
                return null;
            } catch (Exception e) {
                LOGGER.error("Failed to create MCP lifecycle reconciliation lease", e);
                return null;
            }
        }
        if (!current.expired() || StringUtils.isBlank(current.md5)) {
            return null;
        }
        try {
            publishLease(content, true, current.md5);
            LOGGER.info("Took over expired MCP lifecycle reconciliation lease");
            return new ReconciliationLease(owner, contentMd5(content));
        } catch (Exception e) {
            LOGGER.debug("MCP lifecycle reconciliation lease was taken by another node");
            return null;
        }
    }
    
    private LeaseRecord readLease() {
        try {
            ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                RECONCILIATION_LEASE_DATA_ID, INTERNAL_GROUP,
                com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response == null
                || response
                    .getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND
                || StringUtils.isBlank(response.getContent())) {
                return null;
            }
            if (!McpConfigUtils.isConfigFound(response.getStatus())) {
                LOGGER.warn("Unable to inspect MCP lifecycle reconciliation lease: {}",
                    response.getMessage());
                return null;
            }
            return LeaseRecord.parse(response.getContent(), response.getMd5());
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect MCP lifecycle reconciliation lease", e);
            return null;
        }
    }
    
    private void persistProgress(ReconciliationStats stats) {
        try {
            Map<String, Object> progress = new LinkedHashMap<>();
            progress.put("schemaVersion", 1);
            progress.put("state", STATE_SYNCING);
            progress.put("generation", stats.generation);
            progress.put("startedAt", stats.startedAt);
            progress.put("completedAt", stats.completedAt);
            progress.put("namespaces", stats.namespaces);
            progress.put("manifests", stats.manifests);
            progress.put("changed", stats.changed);
            progress.put("orphaned", stats.orphaned);
            progress.put("failed", stats.failed);
            progress.put("completeNamespaceScan", stats.complete);
            progress.put("zeroDifference", stats.zeroDifference());
            progress.put("searchBackfillPending", true);
            progress.put("managedCutoverReady", false);
            if (StringUtils.isNotBlank(stats.lastError)) {
                progress.put("lastError", stats.lastError);
            }
            ConfigForm form = internalForm(RECONCILIATION_PROGRESS_DATA_ID,
                JacksonUtils.toJson(progress));
            form.setType(ConfigType.JSON.getType());
            ConfigRequestInfo requestInfo = new ConfigRequestInfo();
            requestInfo.setUpdateForExist(true);
            configOperationService.publishConfig(form, requestInfo, null);
        } catch (Exception e) {
            LOGGER.warn("Failed to persist MCP lifecycle reconciliation progress", e);
        }
    }
    
    private void publishLease(String content, boolean updateForExist, String casMd5)
        throws Exception {
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        requestInfo.setUpdateForExist(updateForExist);
        requestInfo.setCasMd5(casMd5);
        configOperationService.publishConfig(
            internalForm(RECONCILIATION_LEASE_DATA_ID, content), requestInfo, null);
    }
    
    private ConfigForm internalForm(String dataId, String content) {
        ConfigForm result = new ConfigForm();
        result.setNamespaceId(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID);
        result.setGroup(INTERNAL_GROUP);
        result.setDataId(dataId);
        result.setContent(content);
        result.setSrcUser("nacos");
        return result;
    }
    
    private String leaseContent(String owner) {
        return owner + '|' + (System.currentTimeMillis() + LEASE_DURATION_MILLIS);
    }
    
    private String contentMd5(String content) {
        return MD5Utils.md5Hex(content, com.alibaba.nacos.api.common.Constants.ENCODE);
    }
    
    @Override
    public void destroy() {
        reconciliationExecutor.shutdownNow();
        leaseExecutor.shutdownNow();
    }
    
    private static final class NamespaceScan {
        
        private final List<String> namespaceIds;
        
        private final boolean complete;
        
        private NamespaceScan(List<String> namespaceIds, boolean complete) {
            this.namespaceIds = namespaceIds;
            this.complete = complete;
        }
    }
    
    private static final class ReconciliationStats {
        
        private String generation;
        
        private long startedAt;
        
        private long completedAt;
        
        private int namespaces;
        
        private int manifests;
        
        private int changed;
        
        private int orphaned;
        
        private int failed;
        
        private boolean complete;
        
        private String lastError;
        
        private void recordFailure(String error) {
            failed++;
            complete = false;
            if (StringUtils.isBlank(error)) {
                return;
            }
            lastError = error.length() <= MAX_LAST_ERROR_LENGTH ? error
                : error.substring(0, MAX_LAST_ERROR_LENGTH);
        }
        
        private boolean zeroDifference() {
            return complete && changed == 0 && orphaned == 0 && failed == 0;
        }
    }
    
    private final class ReconciliationLease implements AutoCloseable {
        
        private final String owner;
        
        private final AtomicBoolean owned = new AtomicBoolean(true);
        
        private final ScheduledFuture<?> renewal;
        
        private String currentMd5;
        
        private ReconciliationLease(String owner, String currentMd5) {
            this.owner = owner;
            this.currentMd5 = currentMd5;
            renewal = leaseExecutor.scheduleWithFixedDelay(this::renewSafely,
                LEASE_RENEW_MILLIS, LEASE_RENEW_MILLIS, TimeUnit.MILLISECONDS);
        }
        
        private void assertOwned() {
            if (!owned.get()) {
                throw new IllegalStateException("MCP lifecycle reconciliation lease was lost");
            }
        }
        
        private boolean isOwned() {
            return owned.get();
        }
        
        private synchronized void renewSafely() {
            if (!owned.get()) {
                return;
            }
            try {
                String content = leaseContent(owner);
                publishLease(content, true, currentMd5);
                currentMd5 = contentMd5(content);
            } catch (Exception e) {
                owned.set(false);
                LOGGER.warn("Failed to renew MCP lifecycle reconciliation lease", e);
            }
        }
        
        @Override
        public synchronized void close() {
            renewal.cancel(false);
            if (!owned.compareAndSet(true, false)) {
                return;
            }
            try {
                publishLease(owner + "|0", true, currentMd5);
            } catch (Exception e) {
                LOGGER.warn("Failed to release MCP lifecycle reconciliation lease", e);
            }
        }
    }
    
    private static final class LeaseRecord {
        
        private final long expireAt;
        
        private final String md5;
        
        private LeaseRecord(long expireAt, String md5) {
            this.expireAt = expireAt;
            this.md5 = md5;
        }
        
        private boolean expired() {
            return expireAt <= System.currentTimeMillis();
        }
        
        private static LeaseRecord parse(String content, String md5) {
            String value = content.trim();
            int separator = value.lastIndexOf('|');
            if (separator <= 0 || separator == value.length() - 1) {
                throw new IllegalArgumentException("Invalid MCP reconciliation lease content");
            }
            return new LeaseRecord(Long.parseLong(value.substring(separator + 1)), md5);
        }
    }
}
