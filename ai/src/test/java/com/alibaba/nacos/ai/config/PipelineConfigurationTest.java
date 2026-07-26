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

import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepositoryImpl;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PipelineConfiguration}.
 *
 * @author Nacos
 */
class PipelineConfigurationTest {
    
    @Test
    void shouldCreatePipelineBeans() throws Exception {
        PipelineConfiguration configuration = new PipelineConfiguration();
        AiPipelineModuleConfig moduleConfig = configuration.aiPipelineModuleConfig();
        PublishPipelineManager manager = configuration.publishPipelineManager(moduleConfig);
        PipelineExecutionRepository repository = configuration.pipelineExecutionRepository();
        ExecutorService executorService = configuration.pipelineExecutor();
        
        try {
            PublishPipelineExecutor executor = configuration.publishPipelineExecutor(manager,
                repository, executorService);
            AtomicReference<Thread> worker = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            executorService.execute(() -> {
                worker.set(Thread.currentThread());
                latch.countDown();
            });
            
            assertNotNull(moduleConfig);
            assertTrue(repository instanceof PipelineExecutionRepositoryImpl);
            assertNotNull(executor);
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("pipeline-executor-0", worker.get().getName());
            assertTrue(worker.get().isDaemon());
        } finally {
            executorService.shutdownNow();
        }
    }
    
}
