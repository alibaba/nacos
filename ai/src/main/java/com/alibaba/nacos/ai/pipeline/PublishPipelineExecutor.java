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

package com.alibaba.nacos.ai.pipeline;

import com.alibaba.nacos.ai.pipeline.config.PipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.model.PipelineCallback;
import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Core pipeline execution engine. Asynchronously executes pipeline nodes in serial order,
 * persists execution state, and notifies the caller via callback.
 *
 * @author kiro
 * @since 3.2.0
 */
public class PublishPipelineExecutor {
    
    private static final Logger LOG = LoggerFactory.getLogger(PublishPipelineExecutor.class);
    
    private final PublishPipelineManager pipelineManager;
    
    private final PipelineConfigProvider configProvider;
    
    private final PipelineExecutionRepository executionRepository;
    
    private final ExecutorService asyncExecutor;
    
    public PublishPipelineExecutor(PublishPipelineManager pipelineManager,
        PipelineConfigProvider configProvider,
        PipelineExecutionRepository executionRepository, ExecutorService asyncExecutor) {
        this.pipelineManager = pipelineManager;
        this.configProvider = configProvider;
        this.executionRepository = executionRepository;
        this.asyncExecutor = asyncExecutor;
    }
    
    /**
     * Asynchronously execute the pipeline.
     *
     * <ol>
     *   <li>Check config: if not enabled, return null (no record, no callback)</li>
     *   <li>Get matching services: if empty, return null (no record, no callback)</li>
     *   <li>Create PipelineExecution with IN_PROGRESS status and persist</li>
     *   <li>Return executionId immediately</li>
     *   <li>Submit async task to execute nodes serially, update state, and invoke callback</li>
     * </ol>
     *
     * @param context  pipeline context containing resource metadata
     * @param callback async callback, invoked exactly once when pipeline execution completes
     * @return executionId, or null if pipeline is not enabled or no matching nodes
     */
    public String execute(PublishPipelineContext context, PipelineCallback callback) {
        return execute(context, callback, UUID.randomUUID().toString());
    }
    
    /**
     * Asynchronously execute the pipeline with a pre-generated executionId.
     *
     * <p>Callers who need to write pipeline state (e.g. IN_PROGRESS) before the async task
     * starts should pre-generate an executionId and use this overload.</p>
     *
     * @param context     pipeline context containing resource metadata
     * @param callback    async callback, invoked exactly once when pipeline execution completes
     * @param executionId pre-generated execution identifier
     * @return executionId, or null if pipeline is not enabled or no matching nodes
     */
    public String execute(PublishPipelineContext context, PipelineCallback callback,
        String executionId) {
        // Step 1: Check config
        PipelineConfig config = configProvider.getConfig();
        if (!config.isEnabled()) {
            return null;
        }
        
        // Step 2: Get matching pipeline services
        List<PublishPipelineService> services =
            pipelineManager.getPipelineServices(context.getResourceType(),
                config.getNodes());
        if (services.isEmpty()) {
            return null;
        }
        
        // Step 3: Create execution record
        long now = System.currentTimeMillis();
        
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId(executionId);
        execution.setResourceType(context.getResourceType().name());
        execution.setResourceName(context.getResourceName());
        execution.setNamespaceId(context.getNamespaceId());
        execution.setVersion(context.getVersion());
        execution.setStatus(PipelineExecutionStatus.IN_PROGRESS);
        execution.setPipeline(new ArrayList<>());
        execution.setCreateTime(now);
        execution.setUpdateTime(now);
        
        try {
            executionRepository.save(execution);
        } catch (Exception e) {
            LOG.error("Failed to save initial pipeline execution record for executionId={}",
                executionId, e);
        }
        
        // Step 4: Submit async task
        asyncExecutor.submit(() -> {
            try {
                boolean allPassed = true;
                
                for (PublishPipelineService service : services) {
                    long startTime = System.currentTimeMillis();
                    String executedAt = Instant.now().toString();
                    PipelineNodeResult nodeResult = new PipelineNodeResult();
                    nodeResult.setNodeId(service.pipelineId());
                    nodeResult.setExecutedAt(executedAt);
                    
                    try {
                        PublishPipelineResult pipelineResult = service.execute(context);
                        long endTime = System.currentTimeMillis();
                        nodeResult.setPassed(pipelineResult.isPassed());
                        nodeResult.setMessage(pipelineResult.getMessage());
                        if (pipelineResult.getType() != null) {
                            nodeResult.setMessageType(pipelineResult.getType().getCode());
                        }
                        nodeResult.setCheckpoints(pipelineResult.getCheckpoints());
                        nodeResult.setDurationMs(endTime - startTime);
                        
                        if (!pipelineResult.isPassed()) {
                            allPassed = false;
                        }
                    } catch (Exception e) {
                        long endTime = System.currentTimeMillis();
                        nodeResult.setPassed(false);
                        nodeResult.setMessage(e.getMessage());
                        nodeResult.setDurationMs(endTime - startTime);
                        allPassed = false;
                    }
                    
                    execution.getPipeline().add(nodeResult);
                    execution.setUpdateTime(System.currentTimeMillis());
                    
                    try {
                        executionRepository.update(execution);
                    } catch (Exception e) {
                        LOG.error("Failed to update pipeline execution record for executionId={}",
                            executionId, e);
                    }
                    
                    if (!allPassed) {
                        break;
                    }
                }
                
                // Set final status
                PipelineExecutionStatus finalStatus = allPassed
                    ? PipelineExecutionStatus.APPROVED : PipelineExecutionStatus.REJECTED;
                execution.setStatus(finalStatus);
                execution.setUpdateTime(System.currentTimeMillis());
                
                try {
                    executionRepository.update(execution);
                } catch (Exception e) {
                    LOG.error("Failed to update final pipeline execution status for executionId={}",
                        executionId, e);
                }
                
                // Build result and invoke callback
                PipelineExecutionResult result = new PipelineExecutionResult();
                result.setExecutionId(executionId);
                result.setStatus(finalStatus);
                result.setPipeline(execution.getPipeline());
                callback.onComplete(result);
            } catch (Exception e) {
                LOG.error("Unexpected error during pipeline execution for executionId={}",
                    executionId, e);
                // Ensure callback is called even on unexpected errors
                PipelineExecutionResult result = new PipelineExecutionResult();
                result.setExecutionId(executionId);
                result.setStatus(PipelineExecutionStatus.REJECTED);
                result.setPipeline(execution.getPipeline());
                callback.onComplete(result);
            }
        });
        
        return executionId;
    }
    
    /**
     * Read-only check: whether the pipeline is available for the given resource type.
     *
     * @param resourceType the resource type to check
     * @return true if pipeline is enabled and has matching service nodes
     */
    public boolean isPipelineAvailable(PublishPipelineResourceType resourceType) {
        PipelineConfig config = configProvider.getConfig();
        if (!config.isEnabled()) {
            return false;
        }
        List<PublishPipelineService> services =
            pipelineManager.getPipelineServices(resourceType, config.getNodes());
        return !services.isEmpty();
    }
}
