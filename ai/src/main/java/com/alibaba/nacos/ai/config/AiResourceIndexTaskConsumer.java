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

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
    
    private final ScheduledExecutorService executor =
        ExecutorFactory.Managed.newSingleScheduledExecutorService(
            AiResourceIndexTaskConsumer.class.getCanonicalName(),
            new ThreadFactoryBuilder().daemon(true).nameFormat("nacos-ai-resource-index-%d")
                .build());
    
    private final AiResourceIndexTaskRepository taskRepository;
    
    private final AiResourceIndexService indexBuildService;
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final Executor enhancementExecutor;
    
    @Autowired
    public AiResourceIndexTaskConsumer(AiResourceIndexTaskRepository taskRepository,
        AiResourceIndexService indexBuildService,
        McpServerOperationService mcpServerOperationService) {
        this(taskRepository, indexBuildService, mcpServerOperationService,
            ExecutorUtils.getAiResourceIndexEnhancementExecutor());
    }
    
    AiResourceIndexTaskConsumer(AiResourceIndexTaskRepository taskRepository,
        AiResourceIndexService indexBuildService,
        McpServerOperationService mcpServerOperationService, Executor enhancementExecutor) {
        this.taskRepository = taskRepository;
        this.indexBuildService = indexBuildService;
        this.mcpServerOperationService = mcpServerOperationService;
        this.enhancementExecutor = enhancementExecutor;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null
            || !initialized.compareAndSet(false, true)) {
            return;
        }
        long intervalSeconds = positiveLong(INTERVAL_SECONDS_KEY, DEFAULT_INTERVAL_SECONDS);
        executor.scheduleWithFixedDelay(this::consumeSafely, 0L, intervalSeconds,
            TimeUnit.SECONDS);
    }
    
    void consume() {
        List<AiResourceIndexTask> tasks = taskRepository.findDueTasks(BATCH_SIZE);
        for (AiResourceIndexTask task : tasks) {
            Timestamp leaseUntil = new Timestamp(System.currentTimeMillis() + LEASE_MILLIS);
            if (!taskRepository.claim(task, leaseUntil)) {
                continue;
            }
            if (AiResourceIndexTask.STAGE_LLM_ENHANCEMENT.equals(task.getTaskStage())) {
                submitEnhancement(task);
            } else {
                process(task);
            }
        }
    }
    
    private void submitEnhancement(AiResourceIndexTask task) {
        ScheduledFuture<?> leaseRenewal = executor.scheduleWithFixedDelay(
            () -> renewLease(task), LEASE_RENEW_INTERVAL_SECONDS,
            LEASE_RENEW_INTERVAL_SECONDS, TimeUnit.SECONDS);
        try {
            enhancementExecutor.execute(() -> {
                try {
                    process(task);
                } finally {
                    leaseRenewal.cancel(false);
                }
            });
        } catch (Exception e) {
            leaseRenewal.cancel(false);
            retry(task, e);
            taskRepository.releaseSuperseded(task);
        }
    }
    
    private void renewLease(AiResourceIndexTask task) {
        try {
            taskRepository.renewLease(task,
                new Timestamp(System.currentTimeMillis() + LEASE_MILLIS));
        } catch (Exception e) {
            LOGGER.warn("Failed to renew AI resource index task lease for {}:{} in namespace {}",
                task.getResourceType(), task.getResourceName(), task.getNamespaceId(), e);
        }
    }
    
    private void process(AiResourceIndexTask task) {
        try {
            if (AiResourceIndexTask.STAGE_LLM_ENHANCEMENT.equals(task.getTaskStage())) {
                processEnhancement(task);
            } else {
                processBaseIndex(task);
            }
        } catch (Exception e) {
            retry(task, e);
        } finally {
            taskRepository.releaseSuperseded(task);
        }
    }
    
    private boolean processBaseIndex(AiResourceIndexTask task) throws NacosException {
        if (!convergeBase(task)) {
            return taskRepository.remove(task);
        }
        if (task.isEnhancementRequested() && indexBuildService.isEnhancementRequired()) {
            return taskRepository.advanceToEnhancement(task);
        }
        return taskRepository.complete(task, null);
    }
    
    private boolean processEnhancement(AiResourceIndexTask task) throws Exception {
        if (!indexBuildService.isEnhancementRequired()) {
            return taskRepository.complete(task, null);
        }
        if (!convergeEnhancement(task)) {
            taskRepository.schedule(task.getNamespaceId(), task.getResourceType(),
                task.getResourceName(), true);
            return true;
        }
        return taskRepository.complete(task, indexBuildService.enhancementFingerprint());
    }
    
    private void retry(AiResourceIndexTask task, Exception e) {
        long retrySeconds = retryDelaySeconds(task);
        taskRepository.retry(task,
            new Timestamp(System.currentTimeMillis() + retrySeconds * 1000L), errorMessage(e));
        LOGGER.warn(
            "Failed to converge AI resource index stage {} for {}:{} in namespace {}, "
                + "retry in {}s",
            task.getTaskStage(), task.getResourceType(), task.getResourceName(),
            task.getNamespaceId(), retrySeconds, e);
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
    
    private boolean convergeEnhancement(AiResourceIndexTask task) throws Exception {
        if (!AiResourceConstants.RESOURCE_TYPE_MCP.equals(task.getResourceType())) {
            return indexBuildService.enhanceLatestAiResource(task.getNamespaceId(),
                task.getResourceType(), task.getResourceName());
        }
        try {
            McpServerDetailInfo detail = mcpServerOperationService.getMcpServerDetail(
                task.getNamespaceId(), task.getResourceName(), null, null);
            return detail != null
                && indexBuildService.enhanceMcpServer(task.getNamespaceId(), detail);
        } catch (NacosException e) {
            if (e.getErrCode() != NacosException.NOT_FOUND) {
                throw e;
            }
            return false;
        }
    }
    
    private long retryDelaySeconds(AiResourceIndexTask task) {
        int exponent = Math.min(Math.max(task.getAttemptCount(), 0), 6);
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
        executor.shutdownNow();
    }
}
