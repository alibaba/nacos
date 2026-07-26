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
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.ai.utils.SkillSeedArchiveReader;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * Unit tests for {@link SkillDataBootstrapInitializer}.
 */
@ExtendWith(MockitoExtension.class)
class SkillDataBootstrapInitializerTest {
    
    static {
        EnvUtil.setEnvironment(new org.springframework.core.env.StandardEnvironment());
    }
    
    @Mock
    private AiResourcePersistService aiResourcePersistService;
    
    @Mock
    private SkillOperationService skillOperationService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @TempDir
    private Path tempDir;
    
    private String cachedNacosHome;
    
    private SkillDataBootstrapInitializer initializer;
    
    @BeforeEach
    void setUp() {
        cachedNacosHome = EnvUtil.getNacosHome();
        EnvUtil.setNacosHomePath(tempDir.toString());
        initializer = new SkillDataBootstrapInitializer(aiResourcePersistService,
            skillOperationService, configOperationService, configQueryChainService);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setNacosHomePath(cachedNacosHome);
    }
    
    @Test
    void readSkillPackagesShouldResolveArchiveFromNacosHome() throws Exception {
        Path dataDir = Files.createDirectories(tempDir.resolve("data"));
        writeZip(dataDir.resolve("skills-data.zip"), "team/skill-a/SKILL.md",
            "---\nname: skill-a\n---\nbody");
        
        List<SkillSeedArchiveReader.SkillPackage> packages =
            ReflectionTestUtils.invokeMethod(initializer, "readSkillPackages");
        
        assertEquals(1, packages.size());
        assertEquals("skill-a", packages.get(0).getSkillName());
        assertEquals("team", packages.get(0).getFrom());
    }
    
    @Test
    void buildBootstrapPlanShouldBootstrapWhenAiDataIsEmpty() {
        when(aiResourcePersistService.list("public", null, null, null, 1, 1))
            .thenReturn(page(0));
        when(aiResourcePersistService.find("public", "skill-a", "skill")).thenReturn(null);
        
        Object plan = ReflectionTestUtils.invokeMethod(initializer, "buildBootstrapPlan",
            List.of(skillPackage("skill-a")));
        
        assertTrue(invokeBoolean(plan, "shouldBootstrap"));
        assertEquals(1,
            ((List<?>) ReflectionTestUtils.invokeMethod(plan, "getMissingSkillPackages")).size());
        assertEquals(Integer.valueOf(0),
            ReflectionTestUtils.invokeMethod(plan, "getExistingBuiltInCount"));
    }
    
    @Test
    void buildBootstrapPlanShouldSkipWhenAiDataExistsWithoutBuiltInSkills() {
        when(aiResourcePersistService.list("public", null, null, null, 1, 1))
            .thenReturn(page(1));
        when(aiResourcePersistService.find("public", "skill-a", "skill")).thenReturn(null);
        
        Object plan = ReflectionTestUtils.invokeMethod(initializer, "buildBootstrapPlan",
            List.of(skillPackage("skill-a")));
        
        assertFalse(invokeBoolean(plan, "shouldBootstrap"));
        assertEquals(Integer.valueOf(0),
            ReflectionTestUtils.invokeMethod(plan, "getExistingBuiltInCount"));
    }
    
    @Test
    void buildBootstrapPlanShouldSkipWhenAllBundledSkillsExist() {
        when(aiResourcePersistService.list("public", null, null, null, 1, 1))
            .thenReturn(page(1));
        when(aiResourcePersistService.find("public", "skill-a", "skill"))
            .thenReturn(new AiResource());
        
        Object plan = ReflectionTestUtils.invokeMethod(initializer, "buildBootstrapPlan",
            List.of(skillPackage("skill-a")));
        
        assertFalse(invokeBoolean(plan, "shouldBootstrap"));
        assertEquals(Integer.valueOf(1),
            ReflectionTestUtils.invokeMethod(plan, "getExistingBuiltInCount"));
    }
    
    @Test
    void importBuiltInSkillShouldReportSuccessAndFailure() throws NacosException {
        Object success = ReflectionTestUtils.invokeMethod(initializer, "importBuiltInSkill",
            skillPackage("skill-a"));
        doThrow(new NacosException(500, "failed")).when(skillOperationService)
            .bootstrapSkillFromZip(eq("public"), any(byte[].class), eq("team"));
        Object failure = ReflectionTestUtils.invokeMethod(initializer, "importBuiltInSkill",
            skillPackage("skill-a"));
        
        assertTrue(invokeBoolean(success, "isSuccess"));
        assertFalse(invokeBoolean(failure, "isSuccess"));
        assertEquals("skill-a", ReflectionTestUtils.invokeMethod(failure, "getSkillName"));
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
        verify(configOperationService).deleteConfig("nacos.ai.skills.bootstrap",
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
        verify(configOperationService).deleteConfig("nacos.ai.skills.bootstrap",
            "nacos_internal", "public", null, null, "nacos", null);
    }
    
    private static SkillSeedArchiveReader.SkillPackage skillPackage(String name) {
        return new SkillSeedArchiveReader.SkillPackage(name, "team", "team/" + name,
            new byte[] {1, 2, 3});
    }
    
    private static boolean invokeBoolean(Object target, String method) {
        Boolean value = ReflectionTestUtils.invokeMethod(target, method);
        return Boolean.TRUE.equals(value);
    }
    
    private static Page<AiResource> page(int totalCount) {
        Page<AiResource> page = new Page<>();
        page.setTotalCount(totalCount);
        return page;
    }
    
    private static void writeZip(Path path, String entryName, String content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
