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
import com.alibaba.nacos.ai.model.search.AiResourceIndexTask;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexTaskRepository;
import com.alibaba.nacos.ai.utils.ExecutorUtils;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes durable AI resource index tasks and converges relational and vector indexes.
 *
 * @author nacos
 */
@Component
@ConditionalOnAiResourceSearchEnabled
public class AiResourceIndexTaskConsumer
    implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {
    
    static final String INTERVAL_SECONDS_KEY =
        "nacos.ai.resource.search.index.consumer.interval-seconds";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiResourceIndexTaskConsumer.class);
    
    private static final int BATCH_SIZE = 100;
    
    private static final long DEFAULT_INTERVAL_SECONDS = 5L;
    
    private static final long LEASE_MILLIS = 60_000L;
    
    private static final long LEASE_RENEW_INTERVAL_SECONDS = 20L;
    
    private static final long MAX_RETRY_SECONDS = 300L;
    
    private static final long MAX_ENHANCEMENT_RETRY_SECONDS = 1800L;
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private final AiResourceIndexTaskRepository taskRepository;
    
    private final AiResourceIndexService indexBuildService;
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final Executor enhancementExecutor;
    
    private final Semaphore enhancementSlots;
    
    private final ScheduledExecutorService pollExecutor;
    
    private final ScheduledExecutorService leaseExecutor;
    
    @Autowired
    public AiResourceIndexTaskConsumer(AiResourceIndexTaskRepository taskRepository,
        AiResourceIndexService indexBuildService,
        McpServerOperationService mcpServerOperationService) {
        this(taskRepository, indexBuildService, mcpServerOperationService,
            ExecutorUtils.getAiResourceIndexEnhancementExecutor(),
            ExecutorUtils.getAiResourceIndexEnhancementConcurrency());
    }
    
    AiResourceIndexTaskConsumer(AiResourceIndexTaskRepository taskRepository,
        AiResourceIndexService indexBuildService,
        McpServerOperationService mcpServerOperationService, Executor enhancementExecutor) {
        this(taskRepository, indexBuildService, mcpServerOperationService, enhancementExecutor, 1);
    }
    
    AiResourceIndexTaskConsumer(AiResourceIndexTaskRepository taskRepository,
        AiResourceIndexService indexBuildService,
        McpServerOperationService mcpServerOperationService, Executor enhancementExecutor,
        int enhancementConcurrency) {
        this.taskRepository = taskRepository;
        this.indexBuildService = indexBuildService;
        this.mcpServerOperationService = mcpServerOperationService;
        this.enhancementExecutor = enhancementExecutor;
        this.enhancementSlots = new Semaphore(enhancementConcurrency);
        this.pollExecutor = newScheduler("nacos-ai-resource-index-poll-%d");
        this.leaseExecutor = newScheduler("nacos-ai-resource-index-lease-%d");
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null
            || !initialized.compareAndSet(false, true)) {
            return;
        }
        long intervalSeconds = positiveLong(INTERVAL_SECONDS_KEY, DEFAULT_INTERVAL_SECONDS);
        pollExecutor.scheduleWithFixedDelay(this::consumeSafely, 0L, intervalSeconds,
            TimeUnit.SECONDS);
    }
    
    void consume() {
        List<AiResourceIndexTask> tasks = taskRepository.findDueTasks(BATCH_SIZE);
        for (AiResourceIndexTask task : tasks) {
            boolean enhancement =
                AiResourceIndexTask.STAGE_LLM_ENHANCEMENT.equals(task.getTaskStage());
            if (enhancement && !enhancementSlots.tryAcquire()) {
                continue;
            }
            if (!taskRepository.claim(task, LEASE_MILLIS)) {
                if (enhancement) {
                    enhancementSlots.release();
                }
                continue;
            }
            TaskLease lease = new TaskLease(task);
            if (enhancement) {
                submitEnhancement(task, lease);
            } else {
                try {
                    process(task, lease);
                } finally {
                    lease.close();
                }
            }
        }
    }
    
    private void submitEnhancement(AiResourceIndexTask task, TaskLease lease) {
        try {
            enhancementExecutor.execute(() -> {
                try {
                    if (lease.renewNow()) {
                        process(task, lease);
                    }
                } finally {
                    lease.close();
                    enhancementSlots.release();
                }
            });
        } catch (Exception e) {
            lease.close();
            enhancementSlots.release();
            retry(task, e);
            taskRepository.releaseSuperseded(task);
        }
    }
    
    private void process(AiResourceIndexTask task, TaskLease lease) {
        try {
            if (AiResourceIndexTask.STAGE_LLM_ENHANCEMENT.equals(task.getTaskStage())) {
                processEnhancement(task, lease);
            } else {
                processBaseIndex(task, lease);
            }
        } catch (Exception e) {
            if (lease.isOwned()) {
                retry(task, e);
            }
        } finally {
            taskRepository.releaseSuperseded(task);
        }
    }
    
    private void processBaseIndex(AiResourceIndexTask task, TaskLease lease)
        throws NacosException {
        if (!convergeBase(task)) {
            if (lease.isOwned()) {
                taskRepository.remove(task);
            }
            return;
        }
        if (!lease.isOwned()) {
            return;
        }
        if (task.isEnhancementRequested() && indexBuildService.isEnhancementRequested()) {
            taskRepository.advanceToEnhancement(task);
            return;
        }
        taskRepository.complete(task, null);
    }
    
    private void processEnhancement(AiResourceIndexTask task, TaskLease lease) throws Exception {
        if (!indexBuildService.isEnhancementRequested()) {
            taskRepository.restartFromBase(task, false);
            return;
        }
        String fingerprint = convergeEnhancement(task, lease);
        if (fingerprint == null) {
            if (lease.isOwned()) {
                taskRepository.restartFromBase(task, true);
            }
            return;
        }
        if (lease.isOwned()) {
            taskRepository.complete(task, fingerprint);
        }
    }
    
    private void retry(AiResourceIndexTask task, Exception e) {
        long retrySeconds = retryDelaySeconds(task);
        if (taskRepository.retry(task, retrySeconds * 1000L, errorMessage(e))) {
            LOGGER.warn(
                "Failed to converge AI resource index stage {} for {}:{} in namespace {}, "
                    + "retry in {}s",
                task.getTaskStage(), task.getResourceType(), task.getResourceName(),
                task.getNamespaceId(), retrySeconds, e);
        } else {
            LOGGER.warn(
                "Failed to converge AI resource index stage {} for {}:{} in namespace {}, "
                    + "retry skipped because task revision was superseded",
                task.getTaskStage(), task.getResourceType(), task.getResourceName(),
                task.getNamespaceId(), e);
        }
    }
    
    private void consumeSafely() {
        try {
            consume();
        } catch (Exception e) {
            LOGGER.warn("Failed to poll durable AI resource index tasks", e);
        }
    }
    
    private boolean convergeBase(AiResourceIndexTask task) throws NacosException {
        if (AiResourceConstants.RESOURCE_TYPE_MCP.equals(task.getResourceType())) {
            return convergeMcpBase(task);
        }
        return indexBuildService.rebuildLatestAiResource(task.getNamespaceId(),
            task.getResourceType(),
            task.getResourceName());
    }
    
    private boolean convergeMcpBase(AiResourceIndexTask task) throws NacosException {
        try {
            McpServerDetailInfo detail = mcpServerOperationService.getMcpServerDetail(
                task.getNamespaceId(), task.getResourceName(), null, null);
            if (detail == null) {
                indexBuildService.deleteResource(task.getNamespaceId(),
                    AiResourceConstants.RESOURCE_TYPE_MCP, task.getResourceName());
                return false;
            }
            return indexBuildService.rebuildMcpServer(task.getNamespaceId(), detail);
        } catch (NacosException e) {
            if (e.getErrCode() != NacosException.NOT_FOUND) {
                throw e;
            }
            indexBuildService.deleteResource(task.getNamespaceId(),
                AiResourceConstants.RESOURCE_TYPE_MCP, task.getResourceName());
            return false;
        }
    }
    
    private String convergeEnhancement(AiResourceIndexTask task, TaskLease lease)
        throws Exception {
        if (!AiResourceConstants.RESOURCE_TYPE_MCP.equals(task.getResourceType())) {
            return indexBuildService.enhanceLatestAiResource(task.getNamespaceId(),
                task.getResourceType(), task.getResourceName(), lease::isOwned);
        }
        try {
            McpServerDetailInfo detail = mcpServerOperationService.getMcpServerDetail(
                task.getNamespaceId(), task.getResourceName(), null, null);
            return detail == null ? null
                : indexBuildService.enhanceMcpServer(task.getNamespaceId(), detail,
                    lease::isOwned);
        } catch (NacosException e) {
            if (e.getErrCode() != NacosException.NOT_FOUND) {
                throw e;
            }
            return null;
        }
    }
    
    private long retryDelaySeconds(AiResourceIndexTask task) {
        int exponent = Math.min(Math.max(task.getRetryCount(), 0), 6);
        if (AiResourceIndexTask.STAGE_LLM_ENHANCEMENT.equals(task.getTaskStage())) {
            return Math.min(MAX_ENHANCEMENT_RETRY_SECONDS, 30L << exponent);
        }
        return Math.min(MAX_RETRY_SECONDS, 5L << exponent);
    }
    
    private String errorMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getName() : e.getMessage();
    }
    
    private long positiveLong(String key, long defaultValue) {
        try {
            long value = Long.parseLong(EnvUtil.getProperty(key, String.valueOf(defaultValue)));
            return value > 0 ? value : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
    
    @Override
    public void destroy() {
        pollExecutor.shutdownNow();
        leaseExecutor.shutdownNow();
    }
    
    private ScheduledExecutorService newScheduler(String nameFormat) {
        return ExecutorFactory.Managed.newSingleScheduledExecutorService(
            AiResourceIndexTaskConsumer.class.getCanonicalName() + "." + nameFormat,
            new ThreadFactoryBuilder().daemon(true).nameFormat(nameFormat).build());
    }
    
    private final class TaskLease implements AutoCloseable {
        
        private final AiResourceIndexTask task;
        
        private final AtomicBoolean owned = new AtomicBoolean(true);
        
        private final ScheduledFuture<?> renewal;
        
        private TaskLease(AiResourceIndexTask task) {
            this.task = task;
            this.renewal = leaseExecutor.scheduleWithFixedDelay(this::renewSafely,
                LEASE_RENEW_INTERVAL_SECONDS, LEASE_RENEW_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
        
        private boolean renewNow() {
            renewSafely();
            return isOwned();
        }
        
        private void renewSafely() {
            if (!owned.get()) {
                return;
            }
            try {
                if (!taskRepository.renewLease(task, LEASE_MILLIS)) {
                    owned.set(false);
                }
            } catch (Exception e) {
                owned.set(false);
                LOGGER.warn(
                    "Failed to renew AI resource index task lease for {}:{} in namespace {}",
                    task.getResourceType(), task.getResourceName(), task.getNamespaceId(), e);
            }
        }
        
        private boolean isOwned() {
            return owned.get();
        }
        
        @Override
        public void close() {
            renewal.cancel(false);
        }
    }
}
