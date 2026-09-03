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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationService;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.utils.AgentSpecZipParser;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentSpecDataBootstrapInitializer}.
 */
@ExtendWith(MockitoExtension.class)
class AgentSpecDataBootstrapInitializerTest {
    
    static {
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private AiResourcePersistService aiResourcePersistService;
    
    @Mock
    private AgentSpecOperationService agentSpecOperationService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @TempDir
    private Path tempDir;
    
    private String cachedNacosHome;
    
    private AgentSpecDataBootstrapInitializer initializer;
    
    @BeforeEach
    void setUp() {
        cachedNacosHome = EnvUtil.getNacosHome();
        EnvUtil.setEnvironment(new StandardEnvironment());
        EnvUtil.setNacosHomePath(tempDir.toString());
        initializer = new AgentSpecDataBootstrapInitializer(aiResourcePersistService,
            agentSpecOperationService, configOperationService, configQueryChainService);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setNacosHomePath(cachedNacosHome);
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void readAgentSpecPackagesShouldResolveArchiveFromNacosHome() throws Exception {
        Path dataDir = Files.createDirectories(tempDir.resolve("data"));
        writeZip(dataDir.resolve("agentspec-data.zip"), Map.of(
            "team/agent-a/manifest.json", manifest("agent-a"),
            "team/agent-a/AGENTS.md", "instructions"));
        
        List<AgentSpecZipParser.AgentSpecPackage> packages =
            ReflectionTestUtils.invokeMethod(initializer, "readAgentSpecPackages");
        
        assertEquals(1, packages.size());
        assertEquals("agent-a", packages.get(0).getAgentSpecName());
        assertEquals("team", packages.get(0).getFrom());
    }
    
    @Test
    void buildBootstrapPlanShouldBootstrapWhenAiDataIsEmpty() {
        when(aiResourcePersistService.list("public", null, null, null, 1, 1))
            .thenReturn(page(0));
        when(aiResourcePersistService.find("public", "agent-a", "agentspec")).thenReturn(null);
        
        Object plan = ReflectionTestUtils.invokeMethod(initializer, "buildBootstrapPlan",
            List.of(agentSpecPackage("agent-a")));
        
        assertTrue(invokeBoolean(plan, "shouldBootstrap"));
        assertEquals(1,
            ((List<?>) ReflectionTestUtils.invokeMethod(plan, "getMissingPackages")).size());
    }
    
    @Test
    void buildBootstrapPlanShouldSkipWhenAiDataExistsWithoutBuiltInAgentSpecs() {
        when(aiResourcePersistService.list("public", null, null, null, 1, 1))
            .thenReturn(page(1));
        when(aiResourcePersistService.find("public", "agent-a", "agentspec")).thenReturn(null);
        
        Object plan = ReflectionTestUtils.invokeMethod(initializer, "buildBootstrapPlan",
            List.of(agentSpecPackage("agent-a")));
        
        assertFalse(invokeBoolean(plan, "shouldBootstrap"));
        assertEquals(Integer.valueOf(0),
            ReflectionTestUtils.invokeMethod(plan, "getExistingBuiltInCount"));
    }
    
    @Test
    void buildBootstrapPlanShouldRetryExistingAgentSpecWhenAgentsContentIsMissing()
        throws NacosException {
        when(aiResourcePersistService.list("public", null, null, null, 1, 1))
            .thenReturn(page(1));
        when(aiResourcePersistService.find("public", "agent-a", "agentspec"))
            .thenReturn(new AiResource());
        AgentSpecMeta meta = new AgentSpecMeta();
        meta.setLabels(Map.of("latest", "1.0.0"));
        when(agentSpecOperationService.getAgentSpecDetail("public", "agent-a"))
            .thenReturn(meta);
        AgentSpec current = new AgentSpec();
        current.setResource(Map.of());
        when(agentSpecOperationService.getAgentSpecVersionDetail("public", "agent-a", "1.0.0"))
            .thenReturn(current);
        
        Object plan = ReflectionTestUtils.invokeMethod(initializer, "buildBootstrapPlan",
            List.of(agentSpecPackage("agent-a")));
        
        assertTrue(invokeBoolean(plan, "shouldBootstrap"));
        assertEquals(Integer.valueOf(1),
            ReflectionTestUtils.invokeMethod(plan, "getExistingBuiltInCount"));
    }
    
    @Test
    void agentsContentComparisonShouldIgnorePathsAndCase() {
        AgentSpec bundled = new AgentSpec();
        AgentSpecResource bundledResource = new AgentSpecResource();
        bundledResource.setName("docs/AGENTS.md");
        bundledResource.setContent("instructions");
        bundled.setResource(Map.of("agents", bundledResource));
        AgentSpec current = new AgentSpec();
        AgentSpecResource currentResource = new AgentSpecResource();
        currentResource.setName("agents.md");
        currentResource.setContent("cached");
        current.setResource(Map.of("agents", currentResource));
        
        assertFalse(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(
            AgentSpecDataBootstrapInitializer.class, "isBundledAgentsContentMissing", current,
            bundled)));
    }
    
    @Test
    void importBuiltInAgentSpecShouldReportSuccessAndFailure() throws NacosException {
        Object success = ReflectionTestUtils.invokeMethod(initializer, "importBuiltInAgentSpec",
            agentSpecPackage("agent-a"));
        doThrow(new NacosException(500, "failed")).when(agentSpecOperationService)
            .bootstrapAgentSpecFromZip(eq("public"), any(byte[].class), eq("team"));
        Object failure = ReflectionTestUtils.invokeMethod(initializer, "importBuiltInAgentSpec",
            agentSpecPackage("agent-a"));
        
        assertTrue(invokeBoolean(success, "isSuccess"));
        assertFalse(invokeBoolean(failure, "isSuccess"));
        assertEquals("agent-a",
            ReflectionTestUtils.invokeMethod(failure, "getAgentSpecName"));
    }
    
    @Test
    void tryAcquireBootstrapMarkerShouldReturnTrueWhenPublishSucceeds() throws NacosException {
        assertTrue(invokeBoolean(initializer, "tryAcquireBootstrapMarker"));
        verify(configOperationService).publishConfig(any(ConfigForm.class), any(), eq(null));
    }
    
    @Test
    void tryAcquireBootstrapMarkerShouldReleaseStaleMarkerAndRetry() throws NacosException {
        when(aiResourcePersistService.list("public", null, null, null, 1, 1))
            .thenReturn(page(0));
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent(String.valueOf(System.currentTimeMillis() - 20 * 60 * 1000L));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        doThrow(new ConfigAlreadyExistsException("marker exists")).doReturn(true)
            .when(configOperationService).publishConfig(any(ConfigForm.class), any(), eq(null));
        
        assertTrue(invokeBoolean(initializer, "tryAcquireBootstrapMarker"));
        verify(configOperationService).deleteConfig("nacos.ai.agentspec.bootstrap",
            "nacos_internal", "public", null, null, "nacos", null);
    }
    
    @Test
    void markerHelpersShouldHandleMissingAndInvalidContent() throws NacosException {
        ConfigQueryChainResponse missing = new ConfigQueryChainResponse();
        missing.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(missing);
        
        assertFalse(invokeBoolean(initializer, "isBootstrapMarkerStale"));
        
        ConfigQueryChainResponse invalid = new ConfigQueryChainResponse();
        invalid.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        invalid.setContent("not-a-time");
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(invalid);
        
        assertFalse(invokeBoolean(initializer, "isBootstrapMarkerStale"));
        ReflectionTestUtils.invokeMethod(initializer, "releaseBootstrapMarker");
        verify(configOperationService).deleteConfig("nacos.ai.agentspec.bootstrap",
            "nacos_internal", "public", null, null, "nacos", null);
    }
    
    private static AgentSpecZipParser.AgentSpecPackage agentSpecPackage(String name) {
        return new AgentSpecZipParser.AgentSpecPackage(name, "team", "team/" + name,
            agentSpecZip(name));
    }
    
    private static boolean invokeBoolean(Object target, String method) {
        Boolean value = ReflectionTestUtils.invokeMethod(target, method);
        return Boolean.TRUE.equals(value);
    }
    
    private static byte[] agentSpecZip(String name) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(out)) {
                zos.putNextEntry(new ZipEntry("manifest.json"));
                zos.write(manifest(name).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("AGENTS.md"));
                zos.write("instructions".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
    
    private static String manifest(String name) {
        return "{\"worker\":{\"suggested_name\":\"" + name + "\"}}";
    }
    
    private static Page<AiResource> page(int totalCount) {
        Page<AiResource> page = new Page<>();
        page.setTotalCount(totalCount);
        return page;
    }
    
    private static void writeZip(Path path, Map<String, String> entries) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }
}
