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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.ai.pipeline.config.FilePipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.config.PipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepositoryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring configuration for the publish pipeline components.
 *
 * @author kiro
 */
@Configuration
public class PipelineConfiguration {
    
    @Bean
    public PipelineConfigProvider pipelineConfigProvider() {
        return FilePipelineConfigProvider.getInstance();
    }
    
    @Bean
    public PublishPipelineManager publishPipelineManager(PipelineConfigProvider configProvider) {
        PublishPipelineManager manager = new PublishPipelineManager();
        manager.init(configProvider.getConfig());
        return manager;
    }
    
    @Bean
    public PipelineExecutionRepository pipelineExecutionRepository() {
        return new PipelineExecutionRepositoryImpl();
    }
    
    @Bean("pipelineExecutor")
    public ExecutorService pipelineExecutor() {
        return Executors.newFixedThreadPool(4, new ThreadFactory() {
            
            private final AtomicInteger counter = new AtomicInteger(0);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "pipeline-executor-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }
    
    @Bean
    public PublishPipelineExecutor publishPipelineExecutor(PublishPipelineManager pipelineManager,
        PipelineConfigProvider configProvider, PipelineExecutionRepository executionRepository,
        ExecutorService pipelineExecutor) {
        return new PublishPipelineExecutor(pipelineManager, configProvider, executionRepository,
            pipelineExecutor);
    }
}
