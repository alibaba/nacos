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

package com.alibaba.nacos.airegistry.service;

import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.service.skills.SkillIndexManifestService;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.airegistry.model.skills.SkillsSearchResponse;
import com.alibaba.nacos.airegistry.model.skills.WellKnownSkillsIndex;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static com.alibaba.nacos.ai.constant.Constants.Skills.SEARCH_ACCURATE;
import static com.alibaba.nacos.ai.constant.Constants.Skills.SEARCH_BLUR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link NacosSkillsRegistryService}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class NacosSkillsRegistryServiceTest {
    
    @Mock
    private SkillOperationService skillOperationService;
    
    @Mock
    private SkillIndexManifestService skillIndexManifestService;
    
    private static final String SCHEMA_0_2 =
        "https://schemas.agentskills.io/discovery/0.2.0/schema.json";
    
    private NacosSkillsRegistryService service;
    
    @BeforeEach
    void setUp() {
        service = new NacosSkillsRegistryService(skillOperationService, skillIndexManifestService);
    }
    
    @Test
    void testBuildLegacyIndexFiltersBinarySkill() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(
            List.of(buildSummary("text-skill", 5L), buildSummary("binary-skill", 10L)));
        when(skillOperationService.listSkills(eq("public"), eq((String) null), eq(SEARCH_BLUR),
            eq("download_count"),
            eq(1), eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "text-skill"))
            .thenReturn(buildManifest("v1"));
        when(skillIndexManifestService.query("public", "binary-skill"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "text-skill", "v1")).thenReturn(
            buildTextSkill("text-skill"));
        when(skillOperationService.getSkillVersionDetail("public", "binary-skill", "v1"))
            .thenReturn(
                buildBinarySkill("binary-skill"));
        
        WellKnownSkillsIndex result = service.buildLegacySkillsIndex("public");
        
        assertNotNull(result);
        assertNull(result.getSchema());
        assertEquals(1, result.getSkills().size());
        assertEquals("text-skill", result.getSkills().get(0).getName());
        assertEquals(List.of("SKILL.md", "docs/guide.md"), result.getSkills().get(0).getFiles());
    }
    
    @Test
    void testBuildAgentSkillsIndexUsesVersion020Shape() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("a-simple-skill", 3L),
            buildSummary("z-archive-skill", 5L)));
        when(skillOperationService.listSkills(eq("public"), eq((String) null), eq(SEARCH_BLUR),
            eq("download_count"), eq(1), eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "a-simple-skill"))
            .thenReturn(buildManifest("1.0.0"));
        when(skillIndexManifestService.query("public", "z-archive-skill"))
            .thenReturn(buildManifest("1.2.0"));
        when(skillOperationService.getSkillVersionDetail("public", "a-simple-skill", "1.0.0"))
            .thenReturn(buildSimpleSkill("a-simple-skill"));
        when(skillOperationService.getSkillVersionDetail("public", "z-archive-skill", "1.2.0"))
            .thenReturn(buildTextSkill("z-archive-skill"));
        
        WellKnownSkillsIndex result = service.buildAgentSkillsIndex("public");
        
        assertEquals(SCHEMA_0_2, result.getSchema());
        assertEquals(2, result.getSkills().size());
        assertEquals("a-simple-skill", result.getSkills().get(0).getName());
        assertEquals("skill-md", result.getSkills().get(0).getType());
        assertEquals("a-simple-skill/SKILL.md", result.getSkills().get(0).getUrl());
        assertEquals("1.0.0", result.getSkills().get(0).getVersion());
        assertEquals(sha256("a-simple-skill"), result.getSkills().get(0).getDigest());
        assertNull(result.getSkills().get(0).getFiles());
        assertEquals("z-archive-skill", result.getSkills().get(1).getName());
        assertEquals("archive", result.getSkills().get(1).getType());
        assertEquals("z-archive-skill.zip", result.getSkills().get(1).getUrl());
        assertEquals("1.2.0", result.getSkills().get(1).getVersion());
        assertTrue(result.getSkills().get(1).getDigest().startsWith("sha256:"));
        assertNull(result.getSkills().get(1).getFiles());
    }
    
    @Test
    void testSearchBuildsCliShape() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("b-skill", 2L), buildSummary("a-skill", 9L)));
        when(skillOperationService.listSkills(eq("public"), eq("demo"), eq(SEARCH_BLUR),
            eq("download_count"), eq(1),
            eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "a-skill")).thenReturn(buildManifest("v1"));
        when(skillIndexManifestService.query("public", "b-skill")).thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "a-skill", "v1")).thenReturn(
            buildTextSkill("a-skill"));
        when(skillOperationService.getSkillVersionDetail("public", "b-skill", "v1")).thenReturn(
            buildTextSkill("b-skill"));
        
        SkillsSearchResponse result =
            service.search("public", "demo", 10, "http://localhost/registry/public");
        
        assertEquals(2, result.getSkills().size());
        assertEquals("a-skill", result.getSkills().get(0).getName());
        assertEquals(9L, result.getSkills().get(0).getInstalls());
        assertEquals("http://localhost/registry/public", result.getSkills().get(0).getSource());
    }
    
    @Test
    void testSearchSortsTiedDownloadCountByName() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("z-skill", 5L), buildSummary("a-skill", 5L)));
        when(skillOperationService.listSkills(eq("public"), eq("tie"), eq(SEARCH_BLUR),
            eq("download_count"), eq(1), eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "a-skill")).thenReturn(buildManifest("v1"));
        when(skillIndexManifestService.query("public", "z-skill")).thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "a-skill", "v1"))
            .thenReturn(buildSimpleSkill("a-skill"));
        when(skillOperationService.getSkillVersionDetail("public", "z-skill", "v1"))
            .thenReturn(buildSimpleSkill("z-skill"));
        
        SkillsSearchResponse result = service.search("public", "tie", 10, "source");
        
        assertEquals("a-skill", result.getSkills().get(0).getName());
        assertEquals("z-skill", result.getSkills().get(1).getName());
    }
    
    @Test
    void testGetSkillFileContent() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("demo-skill", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq("demo-skill"), eq(SEARCH_ACCURATE),
            eq("download_count"),
            eq(1), eq(1))).thenReturn(page);
        when(skillIndexManifestService.query("public", "demo-skill"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "demo-skill", "v1")).thenReturn(
            buildTextSkill("demo-skill"));
        
        String markdown = service.getSkillFileContent("public", "demo-skill", "SKILL.md");
        String file = service.getSkillFileContent("public", "demo-skill", "docs/guide.md");
        String missing = service.getSkillFileContent("public", "demo-skill", "docs/missing.md");
        
        assertTrue(markdown.contains("name: demo-skill"));
        assertEquals("guide", file);
        assertNull(missing);
    }
    
    @Test
    void testGetSkillArchiveContentUsesDownloadAndRootArchive() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("demo-skill", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq("demo-skill"), eq(SEARCH_ACCURATE),
            eq("download_count"), eq(1), eq(1))).thenReturn(page);
        when(skillIndexManifestService.query("public", "demo-skill"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.downloadSkillVersion("public", "demo-skill", "v1"))
            .thenReturn(buildTextSkill("demo-skill"));
        
        byte[] zipBytes = service.getSkillArchiveContent("public", "demo-skill");
        
        assertZipEntryContains(zipBytes, "SKILL.md", "name: demo-skill");
        assertZipEntryContains(zipBytes, "docs/guide.md", "guide");
        verify(skillOperationService).downloadSkillVersion("public", "demo-skill", "v1");
    }
    
    @Test
    void testSearchStopsAtLimitAndDefaultsDownloadCount() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(2);
        page.setPageItems(List.of(buildSummary("first", null), buildSummary("second", 2L)));
        when(skillOperationService.listSkills(eq("public"), eq("q"), eq(SEARCH_BLUR),
            eq("download_count"), eq(1), eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "first")).thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "first", "v1")).thenReturn(
            buildSimpleSkill("first"));
        
        SkillsSearchResponse result = service.search("public", "q", 1, "source");
        
        assertEquals(1, result.getSkills().size());
        assertEquals(0L, result.getSkills().get(0).getInstalls());
    }
    
    @Test
    void testBuildIndexStopsOnEmptyPageAndSkipsIneligibleSummaries() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        SkillSummary disabled = buildSummary("disabled", 1L);
        disabled.setEnable(false);
        SkillSummary privateSkill = buildSummary("private", 1L);
        privateSkill.setScope("private");
        SkillSummary offline = buildSummary("offline", 1L);
        offline.setOnlineCnt(0);
        SkillSummary blank = buildSummary("", 1L);
        page.setPageItems(Arrays.asList(null, disabled, privateSkill, offline, blank));
        when(skillOperationService.listSkills(eq("public"), eq((String) null), eq(SEARCH_BLUR),
            eq("download_count"), eq(1), eq(100))).thenReturn(page);
        
        assertTrue(service.buildAgentSkillsIndex("public").getSkills().isEmpty());
        
        Page<SkillSummary> emptyPage = new Page<>();
        emptyPage.setPagesAvailable(1);
        emptyPage.setPageItems(List.of());
        when(skillOperationService.listSkills(eq("public"), eq("empty"), eq(SEARCH_BLUR),
            eq("download_count"), eq(1), eq(100))).thenReturn(emptyPage);
        assertTrue(service.search("public", "empty", 10, "source").getSkills().isEmpty());
    }
    
    @Test
    void testLoadSkillReturnsNullForMissingManifestVersionAndDeniedDetail() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("missing-version", 1L),
            buildSummary("denied", 1L), buildSummary("empty-skill", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq((String) null), eq(SEARCH_BLUR),
            eq("download_count"), eq(1), eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "missing-version"))
            .thenReturn(new SkillIndexManifest());
        when(skillIndexManifestService.query("public", "denied")).thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "denied", "v1"))
            .thenThrow(new NacosException(NacosException.NO_RIGHT, "denied"));
        when(skillIndexManifestService.query("public", "empty-skill"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "empty-skill", "v1"))
            .thenReturn(new Skill());
        
        assertTrue(service.buildAgentSkillsIndex("public").getSkills().isEmpty());
    }
    
    @Test
    void testLoadSkillRethrowsUnexpectedDetailException() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("boom", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq((String) null), eq(SEARCH_BLUR),
            eq("download_count"), eq(1), eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "boom")).thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "boom", "v1"))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "boom"));
        
        assertThrows(NacosException.class, () -> service.buildAgentSkillsIndex("public"));
    }
    
    @Test
    void testGetSkillFileContentNullCases() throws Exception {
        Page<SkillSummary> empty = new Page<>();
        empty.setPageItems(List.of());
        when(skillOperationService.listSkills(eq("public"), eq("missing"), eq(SEARCH_ACCURATE),
            eq("download_count"), eq(1), eq(1))).thenReturn(empty);
        assertNull(service.getSkillFileContent("public", "missing", "SKILL.md"));
        
        Page<SkillSummary> page = new Page<>();
        page.setPageItems(List.of(buildSummary("no-resource", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq("no-resource"), eq(SEARCH_ACCURATE),
            eq("download_count"), eq(1), eq(1))).thenReturn(page);
        when(skillIndexManifestService.query("public", "no-resource"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "no-resource", "v1"))
            .thenReturn(buildSimpleSkill("no-resource"));
        assertNull(service.getSkillFileContent("public", "no-resource", "docs/guide.md"));
        
        Page<SkillSummary> nullEntryPage = new Page<>();
        nullEntryPage.setPageItems(List.of(buildSummary("null-entry", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq("null-entry"), eq(SEARCH_ACCURATE),
            eq("download_count"), eq(1), eq(1))).thenReturn(nullEntryPage);
        when(skillIndexManifestService.query("public", "null-entry"))
            .thenReturn(buildManifest("v1"));
        Skill nullEntry = buildSimpleSkill("null-entry");
        Map<String, SkillResource> resources = new HashMap<>();
        resources.put("null", null);
        nullEntry.setResource(resources);
        when(skillOperationService.getSkillVersionDetail("public", "null-entry", "v1"))
            .thenReturn(nullEntry);
        assertNull(service.getSkillFileContent("public", "null-entry", "docs/guide.md"));
    }
    
    @Test
    void testBuildFilesSkipsNullAndBlankResourcesAndRejectsBinaryExtension() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPagesAvailable(1);
        page.setPageItems(List.of(buildSummary("mixed", 1L), buildSummary("font", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq((String) null), eq(SEARCH_BLUR),
            eq("download_count"), eq(1), eq(100))).thenReturn(page);
        when(skillIndexManifestService.query("public", "mixed")).thenReturn(buildManifest("v1"));
        when(skillIndexManifestService.query("public", "font")).thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "mixed", "v1"))
            .thenReturn(buildMixedResourceSkill("mixed"));
        when(skillOperationService.getSkillVersionDetail("public", "font", "v1"))
            .thenReturn(buildExtensionBinarySkill("font"));
        
        WellKnownSkillsIndex result = service.buildLegacySkillsIndex("public");
        
        assertEquals(1, result.getSkills().size());
        assertEquals(List.of("SKILL.md", "guide.md"), result.getSkills().get(0).getFiles());
    }
    
    @Test
    void testLoadSkillNullAndIneligibleCases() throws Exception {
        Page<SkillSummary> nullPage = null;
        when(skillOperationService.listSkills(eq("public"), eq("null-page"), eq(SEARCH_ACCURATE),
            eq("download_count"), eq(1), eq(1))).thenReturn(nullPage);
        assertNull(service.getSkillFileContent("public", "null-page", "SKILL.md"));
        
        Page<SkillSummary> disabledPage = new Page<>();
        SkillSummary disabled = buildSummary("disabled", 1L);
        disabled.setEnable(false);
        disabledPage.setPageItems(List.of(disabled));
        when(skillOperationService.listSkills(eq("public"), eq("disabled"), eq(SEARCH_ACCURATE),
            eq("download_count"), eq(1), eq(1))).thenReturn(disabledPage);
        assertNull(service.getSkillArchiveContent("public", "disabled"));
        
        Page<SkillSummary> noDescriptionPage = new Page<>();
        noDescriptionPage.setPageItems(List.of(buildSummary("no-description", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq("no-description"),
            eq(SEARCH_ACCURATE), eq("download_count"), eq(1), eq(1)))
            .thenReturn(noDescriptionPage);
        when(skillIndexManifestService.query("public", "no-description"))
            .thenReturn(buildManifest("v1"));
        Skill noDescription = buildSimpleSkill("no-description");
        noDescription.setDescription(" ");
        when(skillOperationService.downloadSkillVersion("public", "no-description", "v1"))
            .thenReturn(noDescription);
        assertNull(service.getSkillArchiveContent("public", "no-description"));
    }
    
    @Test
    void testBuildIndexSkipsNullExportableAndMultiplePages() throws Exception {
        Page<SkillSummary> firstPage = new Page<>();
        firstPage.setPagesAvailable(2);
        firstPage.setPageItems(List.of(buildSummary("not-found", 1L)));
        Page<SkillSummary> secondPage = new Page<>();
        secondPage.setPagesAvailable(2);
        secondPage.setPageItems(List.of(buildSummary("second-page", 2L)));
        when(skillOperationService.listSkills(eq("public"), eq((String) null), eq(SEARCH_BLUR),
            eq("download_count"), eq(1), eq(100))).thenReturn(firstPage);
        when(skillOperationService.listSkills(eq("public"), eq((String) null), eq(SEARCH_BLUR),
            eq("download_count"), eq(2), eq(100))).thenReturn(secondPage);
        when(skillIndexManifestService.query("public", "not-found"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "not-found", "v1"))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "not found"));
        when(skillIndexManifestService.query("public", "second-page"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.getSkillVersionDetail("public", "second-page", "v1"))
            .thenReturn(buildSimpleSkill("second-page"));
        
        WellKnownSkillsIndex result = service.buildAgentSkillsIndex("public");
        
        assertEquals(1, result.getSkills().size());
        assertEquals("second-page", result.getSkills().get(0).getName());
    }
    
    @Test
    void testArchiveCreationFailureForUnsafeResourcePath() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPageItems(List.of(buildSummary("unsafe", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq("unsafe"), eq(SEARCH_ACCURATE),
            eq("download_count"), eq(1), eq(1))).thenReturn(page);
        when(skillIndexManifestService.query("public", "unsafe"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.downloadSkillVersion("public", "unsafe", "v1"))
            .thenReturn(buildUnsafeResourceSkill("unsafe"));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.getSkillArchiveContent("public", "unsafe"));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
    }
    
    @Test
    void testArchiveWritesEmptyResourceForNullContent() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setPageItems(List.of(buildSummary("empty-file", 1L)));
        when(skillOperationService.listSkills(eq("public"), eq("empty-file"), eq(SEARCH_ACCURATE),
            eq("download_count"), eq(1), eq(1))).thenReturn(page);
        when(skillIndexManifestService.query("public", "empty-file"))
            .thenReturn(buildManifest("v1"));
        when(skillOperationService.downloadSkillVersion("public", "empty-file", "v1"))
            .thenReturn(buildMixedResourceSkill("empty-file"));
        
        byte[] zipBytes = service.getSkillArchiveContent("public", "empty-file");
        
        assertZipEntryContains(zipBytes, "guide.md", "");
    }
    
    @Test
    void testPrivateResourceHelpers() throws Exception {
        Method isBinaryResource =
            NacosSkillsRegistryService.class.getDeclaredMethod("isBinaryResource",
                SkillResource.class);
        isBinaryResource.setAccessible(true);
        SkillResource noExtension = new SkillResource();
        noExtension.setName("README");
        assertEquals(false, isBinaryResource.invoke(service, noExtension));
        SkillResource blankName = new SkillResource();
        blankName.setName(" ");
        assertEquals(false, isBinaryResource.invoke(service, blankName));
        SkillResource trailingDot = new SkillResource();
        trailingDot.setName("README.");
        assertEquals(false, isBinaryResource.invoke(service, trailingDot));
        
        Method buildRelativePath =
            NacosSkillsRegistryService.class.getDeclaredMethod("buildRelativePath",
                SkillResource.class);
        buildRelativePath.setAccessible(true);
        SkillResource rootFile = new SkillResource();
        rootFile.setName("guide.md");
        assertEquals("guide.md", buildRelativePath.invoke(service, rootFile));
        
        Method encodePath =
            NacosSkillsRegistryService.class.getDeclaredMethod("encodePath", String.class);
        encodePath.setAccessible(true);
        assertEquals("docs/a%20b.md", encodePath.invoke(service, "docs/a b.md"));
        
        Method buildFiles =
            NacosSkillsRegistryService.class.getDeclaredMethod("buildFiles", Skill.class);
        buildFiles.setAccessible(true);
        assertEquals(List.of("SKILL.md"), buildFiles.invoke(service, buildEmptyResourceSkill()));
    }
    
    private SkillSummary buildSummary(String name, Long downloadCount) {
        SkillSummary result = new SkillSummary();
        result.setNamespaceId("public");
        result.setName(name);
        result.setDescription(name + " description");
        result.setEnable(true);
        result.setScope(VisibilityConstants.SCOPE_PUBLIC);
        result.setOnlineCnt(1);
        result.setDownloadCount(downloadCount);
        return result;
    }
    
    private SkillIndexManifest buildManifest(String version) {
        SkillIndexManifest manifest = new SkillIndexManifest();
        manifest.setLabels(Map.of(SkillIndexManifest.LABEL_LATEST, version));
        manifest.setVersions(Map.of(version, List.of("ignored")));
        return manifest;
    }
    
    private Skill buildTextSkill(String name) {
        Skill result = new Skill();
        result.setNamespaceId("public");
        result.setName(name);
        result.setDescription(name + " description");
        result.setSkillMd(
            "---\nname: " + name + "\ndescription: " + name + " description\n---\n\n# " + name);
        SkillResource resource = new SkillResource();
        resource.setType("docs");
        resource.setName("guide.md");
        resource.setContent("guide");
        result.setResource(Map.of("docs::guide.md", resource));
        return result;
    }
    
    private Skill buildSimpleSkill(String name) {
        Skill result = new Skill();
        result.setNamespaceId("public");
        result.setName(name);
        result.setDescription(name + " description");
        result.setSkillMd(
            "---\nname: " + name + "\ndescription: " + name + " description\n---\n\n# " + name);
        return result;
    }
    
    private Skill buildBinarySkill(String name) {
        Skill result = buildTextSkill(name);
        SkillResource binary = new SkillResource();
        binary.setType("assets");
        binary.setName("logo.png");
        binary.setContent("AA==");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("encoding", "base64");
        binary.setMetadata(metadata);
        result.setResource(Map.of("assets::logo.png", binary));
        return result;
    }
    
    private Skill buildMixedResourceSkill(String name) {
        Skill result = buildSimpleSkill(name);
        SkillResource blankName = new SkillResource();
        blankName.setName(" ");
        SkillResource guide = new SkillResource();
        guide.setName("guide.md");
        guide.setContent(null);
        result.setResource(new HashMap<>());
        result.getResource().put("null", null);
        result.getResource().put("blank", blankName);
        result.getResource().put("guide", guide);
        return result;
    }
    
    private Skill buildExtensionBinarySkill(String name) {
        Skill result = buildSimpleSkill(name);
        SkillResource binary = new SkillResource();
        binary.setName("font.ttf");
        binary.setContent("font");
        result.setResource(Map.of("font", binary));
        return result;
    }
    
    private Skill buildEmptyResourceSkill() {
        Skill result = buildSimpleSkill("empty-resource");
        result.setResource(Collections.emptyMap());
        return result;
    }
    
    private Skill buildUnsafeResourceSkill(String name) {
        Skill result = buildSimpleSkill(name);
        SkillResource unsafe = new SkillResource();
        unsafe.setName("../evil.md");
        unsafe.setContent("evil");
        result.setResource(Map.of("unsafe", unsafe));
        return result;
    }
    
    private String sha256(String skillName) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(buildSimpleSkill(skillName).getSkillMd()
            .getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder("sha256:");
        for (byte each : hash) {
            result.append(String.format("%02x", each & 0xff));
        }
        return result.toString();
    }
    
    private void assertZipEntryContains(byte[] zipBytes, String entryName, String expected)
        throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes),
            StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    zip.transferTo(output);
                    assertTrue(output.toString(StandardCharsets.UTF_8).contains(expected));
                    return;
                }
            }
        }
        throw new AssertionError("Zip entry not found: " + entryName);
    }
}
