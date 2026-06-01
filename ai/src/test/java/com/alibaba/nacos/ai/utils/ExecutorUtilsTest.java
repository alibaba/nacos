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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ExecutorUtils} that guard the dedicated IO executors used
 * by Skill and AgentSpec storage write paths.
 *
 * <p>These tests pin the contract relied upon by the concurrent save implementation
 * in {@code AgentSpecOperationServiceImpl} / {@code SkillOperationServiceImpl}: each
 * resource type owns an isolated executor, so contention from one resource type
 * cannot starve the other.</p>
 */
class ExecutorUtilsTest {
    
    private static ConfigurableEnvironment cachedEnvironment;
    
    @BeforeAll
    static void initEnvBeforeClassLoad() {
        // ExecutorUtils' static initializer reads EnvUtil; it must be primed before
        // the test methods trigger class loading, otherwise <clinit> will fail.
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @AfterAll
    static void restoreEnv() {
        if (cachedEnvironment != null) {
            EnvUtil.setEnvironment(cachedEnvironment);
        }
    }
    
    @Test
    void agentSpecExecutorShouldBeAvailable() {
        ExecutorService executor = ExecutorUtils.getAgentSpecStorageIoExecutor();
        assertNotNull(executor, "AgentSpec storage IO executor must be initialized");
    }
    
    @Test
    void agentSpecAndSkillExecutorsShouldBeIsolated() {
        ExecutorService agentSpecExecutor = ExecutorUtils.getAgentSpecStorageIoExecutor();
        ExecutorService skillExecutor = ExecutorUtils.getSkillStorageIoExecutor();
        assertNotNull(agentSpecExecutor);
        assertNotNull(skillExecutor);
        assertNotSame(skillExecutor, agentSpecExecutor,
            "AgentSpec and Skill executors must not share the same underlying pool");
    }
    
    @Test
    void agentSpecExecutorThreadNameShouldUseDedicatedPrefix() throws Exception {
        AtomicReference<String> capturedName = new AtomicReference<>();
        ExecutorUtils.getAgentSpecStorageIoExecutor().submit(() -> {
            capturedName.set(Thread.currentThread().getName());
        }).get();
        String name = capturedName.get();
        assertNotNull(name);
        assertTrue(name.startsWith("com.alibaba.nacos.ai.agentspec.storage-io"),
            "Unexpected thread name for AgentSpec storage IO executor: " + name);
    }
    
    @Test
    void skillExecutorThreadNameShouldRemainUnchanged() throws Exception {
        AtomicReference<String> capturedName = new AtomicReference<>();
        ExecutorUtils.getSkillStorageIoExecutor().submit(() -> {
            capturedName.set(Thread.currentThread().getName());
        }).get();
        String name = capturedName.get();
        assertNotNull(name);
        assertTrue(name.startsWith("com.alibaba.nacos.ai.skill.storage-io"),
            "Skill storage IO executor thread name should remain stable: " + name);
    }
}
