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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.PropertyUtils;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.concurrent.ExecutorService;

/**
 * Executor utilities for AI module.
 */
public final class ExecutorUtils {
    
    private ExecutorUtils() {
    }
    
    /**
     * System config key for async concurrency when persisting skill resource files to storage.
     */
    public static final String SKILL_STORAGE_IO_CONCURRENCY_CONFIG_KEY =
        "nacos.ai.skill.storage.io.concurrency";
    
    /**
     * System config key for async concurrency when persisting AgentSpec resource files to storage.
     */
    public static final String AGENTSPEC_STORAGE_IO_CONCURRENCY_CONFIG_KEY =
        "nacos.ai.agentspec.storage.io.concurrency";
    
    /**
     * Default concurrency for async skill resource persistence.
     */
    private static final int DEFAULT_SKILL_STORAGE_IO_CONCURRENCY =
        PropertyUtils.getProcessorsCount();
    
    /**
     * Default concurrency for async AgentSpec resource persistence.
     */
    private static final int DEFAULT_AGENTSPEC_STORAGE_IO_CONCURRENCY =
        PropertyUtils.getProcessorsCount();
    
    private static final ExecutorService SKILL_STORAGE_IO_EXECUTOR =
        ExecutorFactory.Managed.newFixedExecutorService(
            ExecutorUtils.class.getCanonicalName(),
            resolveSkillStorageIoConcurrency(),
            new NameThreadFactory("com.alibaba.nacos.ai.skill.storage-io"));
    
    private static final ExecutorService AGENTSPEC_STORAGE_IO_EXECUTOR =
        ExecutorFactory.Managed.newFixedExecutorService(
            ExecutorUtils.class.getCanonicalName(),
            resolveAgentSpecStorageIoConcurrency(),
            new NameThreadFactory("com.alibaba.nacos.ai.agentspec.storage-io"));
    
    /**
     * Executor for async storage IO of skill resources.
     */
    public static ExecutorService getSkillStorageIoExecutor() {
        return SKILL_STORAGE_IO_EXECUTOR;
    }
    
    /**
     * Executor for async storage IO of AgentSpec resources.
     */
    public static ExecutorService getAgentSpecStorageIoExecutor() {
        return AGENTSPEC_STORAGE_IO_EXECUTOR;
    }
    
    private static int resolveSkillStorageIoConcurrency() {
        String val = EnvUtil.getProperty(SKILL_STORAGE_IO_CONCURRENCY_CONFIG_KEY,
            String.valueOf(DEFAULT_SKILL_STORAGE_IO_CONCURRENCY));
        try {
            return Integer.max(1, Integer.parseInt(val));
        } catch (Exception ignored) {
            return DEFAULT_SKILL_STORAGE_IO_CONCURRENCY;
        }
    }
    
    private static int resolveAgentSpecStorageIoConcurrency() {
        String val = EnvUtil.getProperty(AGENTSPEC_STORAGE_IO_CONCURRENCY_CONFIG_KEY,
            String.valueOf(DEFAULT_AGENTSPEC_STORAGE_IO_CONCURRENCY));
        try {
            return Integer.max(1, Integer.parseInt(val));
        } catch (Exception ignored) {
            return DEFAULT_AGENTSPEC_STORAGE_IO_CONCURRENCY;
        }
    }
}
