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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.PublishPipelineManager;
import com.alibaba.nacos.ai.pipeline.config.PipelineConfigProvider;
import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Unit tests guarding the concurrent save behaviour introduced in
 * {@link AgentSpecOperationServiceImpl#saveAgentSpecFilesConcurrently}.
 *
 * <p>The tests directly drive the private helper through reflection so that they
 * remain insulated from the surrounding draft / publish business logic. Each test
 * pins one invariant of the refactor: write count, executor isolation, and exception
 * unwrapping. Together they ensure the parallelization does not regress correctness
 * compared to the previous serial implementation.</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentSpecConcurrentSaveTest {
    
    @Mock
    private AiResourceStorage storage;
    
    @Mock
    private AiResourcePersistService aiResourcePersistService;
    
    @Mock
    private AiResourceVersionPersistService aiResourceVersionPersistService;
    
    @Mock
    private PipelineConfigProvider pipelineConfigProvider;
    
    @Mock
    private PipelineExecutionRepository pipelineExecutionRepository;
    
    private AgentSpecOperationServiceImpl service;
    
    private static final org.springframework.core.env.ConfigurableEnvironment CACHED_ENVIRONMENT =
        EnvUtil.getEnvironment();
    
    @BeforeAll
    static void initEnvBeforeClassLoad() {
        // ExecutorUtils' static initializer reads EnvUtil during class loading; if EnvUtil
        // has not been primed beforehand, the <clinit> will throw NPE and permanently mark
        // the class as NoClassDefFoundError, breaking every subsequent test in the JVM.
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @AfterAll
    static void restoreEnv() {
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        AiResourceStorageRouter.reset();
        lenient().when(storage.type()).thenReturn("nacos_config");
        AiResourceStorageRouter.join(storage);
        PipelineConfig disabledConfig = new PipelineConfig();
        disabledConfig.setEnabled(false);
        lenient().when(pipelineConfigProvider.getConfig()).thenReturn(disabledConfig);
        PublishPipelineExecutor publishPipelineExecutor = new PublishPipelineExecutor(
            new PublishPipelineManager(), pipelineConfigProvider, pipelineExecutionRepository,
            Executors.newSingleThreadExecutor());
        service = new AgentSpecOperationServiceImpl(aiResourcePersistService,
            aiResourceVersionPersistService, publishPipelineExecutor,
            new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
                pipelineExecutionRepository));
    }
    
    @AfterEach
    void tearDown() {
        AiResourceStorageRouter.reset();
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void saveAgentSpecWithoutResourcesShouldPersistOnlyManifest() throws Exception {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("only-main-agent");
        agentSpec.setDescription("manifest only");
        
        invokeConcurrentSave("public", agentSpec, "v1", System.currentTimeMillis());
        
        verify(storage, org.mockito.Mockito.times(1))
            .save(any(StorageKey.class), any(byte[].class));
    }
    
    @Test
    void saveAgentSpecWithMultipleResourcesShouldPersistMainAndAllResources() throws Exception {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("multi-resource-agent");
        agentSpec.setDescription("with resources");
        Map<String, AgentSpecResource> resources = new LinkedHashMap<>();
        resources.put("res-a", buildResource("res-a", "config", "alpha"));
        resources.put("res-b", buildResource("res-b", "skill", "beta"));
        resources.put("res-c", buildResource("res-c", "other", "gamma"));
        agentSpec.setResource(resources);
        
        Set<String> capturedKeys = ConcurrentHashMap.newKeySet();
        lenient().doAnswer(invocation -> {
            StorageKey key = invocation.getArgument(0);
            capturedKeys.add(key.getKey());
            return null;
        }).when(storage).save(any(StorageKey.class), any(byte[].class));
        
        invokeConcurrentSave("public", agentSpec, "v1", System.currentTimeMillis());
        
        verify(storage, org.mockito.Mockito.times(4))
            .save(any(StorageKey.class), any(byte[].class));
        // Each StorageKey must be unique to avoid resources stomping each other when
        // running concurrently in production.
        assertEquals(4, capturedKeys.size(),
            "Each saved file must use a distinct StorageKey, captured: " + capturedKeys);
    }
    
    @Test
    void allSavesShouldRunOnAgentSpecStorageIoExecutor() throws Exception {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("threading-agent");
        Map<String, AgentSpecResource> resources = new LinkedHashMap<>();
        resources.put("a", buildResource("a", "config", "x"));
        resources.put("b", buildResource("b", "config", "y"));
        agentSpec.setResource(resources);
        
        ConcurrentLinkedQueue<String> threadNames = new ConcurrentLinkedQueue<>();
        lenient().doAnswer(invocation -> {
            threadNames.add(Thread.currentThread().getName());
            return null;
        }).when(storage).save(any(StorageKey.class), any(byte[].class));
        
        invokeConcurrentSave("public", agentSpec, "v1", System.currentTimeMillis());
        
        // 1 manifest + 2 resources = 3 save invocations, all routed to the dedicated executor.
        assertEquals(3, threadNames.size());
        for (String name : threadNames) {
            assertNotNull(name);
            assertTrue(name.startsWith("com.alibaba.nacos.ai.agentspec.storage-io"),
                "save() must run on the AgentSpec storage IO executor, but got: " + name);
        }
    }
    
    @Test
    void nacosExceptionFromSaveTaskShouldBeUnwrapped() throws Exception {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("failing-agent");
        Map<String, AgentSpecResource> resources = new LinkedHashMap<>();
        resources.put("boom", buildResource("boom", "config", "payload"));
        agentSpec.setResource(resources);
        
        NacosException expected = new NacosException(NacosException.SERVER_ERROR, "boom");
        lenient().doThrow(expected).when(storage).save(any(StorageKey.class), any(byte[].class));
        
        InvocationTargetException ite = assertThrows(InvocationTargetException.class,
            () -> invokeConcurrentSave("public", agentSpec, "v1", System.currentTimeMillis()));
        Throwable cause = ite.getCause();
        assertNotNull(cause, "Reflection invocation must propagate the underlying cause");
        assertTrue(cause instanceof NacosException,
            "Expected NacosException unwrapped from CompletionException, got: " + cause);
        assertSame(expected, cause,
            "The NacosException raised by storage.save must be propagated as-is");
    }
    
    private AgentSpecResource buildResource(String name, String type, String content) {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName(name);
        resource.setType(type);
        resource.setContent(content);
        resource.setMetadata(new HashMap<>());
        return resource;
    }
    
    private void invokeConcurrentSave(String namespaceId, AgentSpec agentSpec, String version,
        long uniformId) throws Exception {
        Method method = AgentSpecOperationServiceImpl.class
            .getDeclaredMethod("saveAgentSpecFilesConcurrently", String.class, AgentSpec.class,
                String.class, long.class);
        method.setAccessible(true);
        method.invoke(service, namespaceId, agentSpec, version, uniformId);
    }
}
