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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises Skill frontmatter against stateful repository doubles and the real lifecycle manager.
 *
 * @author Zhengcy05
 */
class SkillFrontMatterTest {
    
    private final Map<String, AiResource> resources = new LinkedHashMap<>();
    
    private final Map<String, AiResourceVersion> versions = new LinkedHashMap<>();
    
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    
    private final AiResourcePersistService metas = mock(AiResourcePersistService.class);
    
    private final AiResourceVersionPersistService rows =
        mock(AiResourceVersionPersistService.class);
    
    private final AiResourceStorage storage = mock(AiResourceStorage.class);
    
    private final SkillIndexManifestService manifests = mock(SkillIndexManifestService.class);
    
    private MockedStatic<VisibilityHelper> visibility;
    
    private ConfigurableEnvironment environment;
    
    private SkillOperationServiceImpl service;
    
    private Runnable cacheConflict;
    
    private boolean rejectCache;
    
    @BeforeEach
    void setUp() throws Exception {
        environment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
        visibility = mockStatic(VisibilityHelper.class);
        visibility.when(VisibilityHelper::findVisibilityService).thenReturn(Optional.empty());
        visibility.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("owner");
        visibility.when(() -> VisibilityHelper.canReadResource(any())).thenReturn(true);
        AiResourceStorageRouter.reset();
        when(storage.type()).thenReturn("nacos_config");
        AiResourceStorageRouter.join(storage);
        doAnswer(call -> {
            files.put(call.<StorageKey>getArgument(0).getKey(), call.getArgument(1));
            return null;
        }).when(storage).save(any(), any());
        when(storage.get(any()))
            .thenAnswer(call -> files.get(call.<StorageKey>getArgument(0).getKey()));
        when(metas.find(anyString(), anyString(), eq("skill")))
            .thenAnswer(call -> copy(resources.get(call.<String>getArgument(1)), AiResource.class));
        when(metas.insert(any())).thenAnswer(call -> {
            AiResource meta = call.getArgument(0);
            resources.put(meta.getName(), copy(meta, AiResource.class));
            return 1L;
        });
        when(metas.updateMetaCas(anyString(), anyString(), eq("skill"), anyLong(), any()))
            .thenAnswer(call -> {
                AiResource current = resources.get(call.<String>getArgument(1));
                AiResource update = call.getArgument(4);
                boolean cacheUpdate = !java.util.Objects.equals(current.getExt(), update.getExt());
                if (cacheUpdate && rejectCache) {
                    return false;
                }
                if (cacheUpdate && cacheConflict != null) {
                    Runnable conflict = cacheConflict;
                    cacheConflict = null;
                    conflict.run();
                    return false;
                }
                if (!current.getMetaVersion().equals(call.<Long>getArgument(3))) {
                    return false;
                }
                current.setStatus(update.getStatus());
                current.setDesc(update.getDesc());
                current.setBizTags(update.getBizTags());
                current.setExt(update.getExt());
                current.setVersionInfo(update.getVersionInfo());
                current.setMetaVersion(current.getMetaVersion() + 1);
                return true;
            });
        when(metas.list(any(QueryCondition.class), anyInt(), anyInt())).thenAnswer(call -> page(
            resources.values().stream().map(value -> copy(value, AiResource.class))
                .collect(Collectors.toList())));
        when(rows.insert(any())).thenAnswer(call -> {
            AiResourceVersion version = call.getArgument(0);
            versions.put(key(version.getName(), version.getVersion()),
                copy(version, AiResourceVersion.class));
            return 1L;
        });
        when(rows.find(anyString(), anyString(), eq("skill"), anyString()))
            .thenAnswer(call -> copy(versions.get(key(call.getArgument(1), call.getArgument(3))),
                AiResourceVersion.class));
        when(rows.list(anyString(), anyString(), eq("skill"), nullable(String.class), anyInt(),
            anyInt()))
            .thenAnswer(call -> page(versions.values().stream()
                .filter(row -> row.getName().equals(call.getArgument(1)))
                .filter(row -> call.getArgument(3) == null
                    || row.getStatus().equals(call.getArgument(3)))
                .map(row -> copy(row, AiResourceVersion.class)).collect(Collectors.toList())));
        when(rows.updateStorage(anyString(), anyString(), eq("skill"), anyString(), anyString()))
            .thenAnswer(call -> {
                versions.get(key(call.getArgument(1), call.getArgument(3)))
                    .setStorage(call.getArgument(4));
                return 1;
            });
        when(rows.updateStatus(anyString(), anyString(), eq("skill"), anyString(), anyString()))
            .thenAnswer(call -> {
                versions.get(key(call.getArgument(1), call.getArgument(3)))
                    .setStatus(call.getArgument(4));
                return 1;
            });
        when(rows.delete(anyString(), anyString(), eq("skill"), anyString())).thenAnswer(call -> {
            versions.remove(key(call.getArgument(1), call.getArgument(3)));
            return 1;
        });
        when(manifests.loadForUpdate(anyString(), anyString())).thenAnswer(call -> {
            SkillIndexManifest manifest = new SkillIndexManifest();
            manifest.setLabels(new HashMap<>());
            manifest.setVersions(new HashMap<>());
            return manifest;
        });
        service = new SkillOperationServiceImpl(metas, rows, mock(PublishPipelineExecutor.class),
            manifests,
            new AiResourceManager(metas, rows, mock(PipelineExecutionRepository.class)));
    }
    
    @AfterEach
    void tearDown() {
        visibility.close();
        AiResourceStorageRouter.reset();
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void creationUpdateAndDraftDeletion() throws Exception {
        create("one", "1.0.0", "First");
        assertEquals("First", summary("one").getFrontMatter().get("alias"));
        AiResource meta = resources.get("one");
        Map<String, Object> ext = JacksonUtils.toObj(meta.getExt(), Map.class);
        ext.put("custom", "preserved");
        meta.setExt(JacksonUtils.toJson(ext));
        service.updateDraft("public", skill("one", "Updated"), null);
        assertEquals("Updated", summary("one").getFrontMatter().get("alias"));
        assertTrue(resources.get("one").getExt().contains("preserved"));
        assertTrue(versions.get(key("one", "1.0.0")).getStorage().contains("Updated"));
        service.deleteDraft("public", "one");
        assertNull(summary("one").getFrontMatter());
        assertEquals("{\"custom\":\"preserved\"}", resources.get("one").getExt());
    }
    
    @Test
    void snapshotStoresOnlyCustomFieldsAndResponseBuildsReservedFields() throws Exception {
        create("one", "1.0.0", "Display");
        Map<String, String> versionFrontMatter = versionFrontMatter("one", "1.0.0");
        assertEquals("one", versionFrontMatter.get("name"));
        assertEquals("Description", versionFrontMatter.get("description"));
        
        Map<String, Object> ext = JacksonUtils.toObj(resources.get("one").getExt(), Map.class);
        Map<String, String> cached = cachedFrontMatter(ext);
        assertFalse(cached.containsKey("name"));
        assertFalse(cached.containsKey("description"));
        assertFalse(cached.containsKey("version"));
        cached.put("name", "cached-name");
        cached.put("description", "cached-description");
        cached.put("version", "cached-version");
        resources.get("one").setExt(JacksonUtils.toJson(ext));
        
        SkillSummary summary = summary("one");
        assertEquals("one", summary.getFrontMatter().get("name"));
        assertEquals(summary.getDescription(), summary.getFrontMatter().get("description"));
        assertEquals("1.0.0", summary.getFrontMatter().get("version"));
        assertEquals(Boolean.FALSE, summary.getFrontMatterTruncated());
    }
    
    @Test
    void snapshotLimitsEntriesAndPrioritizesStandardFields() throws Exception {
        StringBuilder markdown = frontMatterHeader("one");
        for (int i = 0; i < 70; i++) {
            markdown.append("custom-").append(i).append(": value-").append(i).append('\n');
        }
        markdown.append("alias: Preferred alias\nlicense: Apache-2.0\n")
            .append("compatibility: Nacos 3.x\nallowed-tools: Read Write\n")
            .append("metadata:\n  preferred: preferred value\n")
            .append("---\nInstructions\n");
        service.createDraft("public", "one", null, "1.0.0", skill("one", markdown), null);
        
        Map<String, Object> ext = JacksonUtils.toObj(resources.get("one").getExt(), Map.class);
        Map<String, String> cached = cachedFrontMatter(ext);
        assertEquals(64, cached.size());
        assertEquals("Preferred alias", cached.get("alias"));
        assertEquals("Apache-2.0", cached.get("license"));
        assertEquals("Nacos 3.x", cached.get("compatibility"));
        assertEquals("Read Write", cached.get("allowed-tools"));
        assertEquals("preferred value", cached.get("metadata.preferred"));
        assertTrue(cached.keySet().stream().filter(key -> key.startsWith("custom-")).count() <= 59);
        SkillSummary summary = summary("one");
        assertEquals(Boolean.TRUE, summary.getFrontMatterTruncated());
        assertEquals(67, summary.getFrontMatter().size());
    }
    
    @Test
    void snapshotLimitsKeysValuesAndSerializedSizeWithoutChangingVersionMetadata()
        throws Exception {
        String longValue = repeat('x', 1100);
        String longKey = repeat('k', 129);
        StringBuilder markdown = frontMatterHeader("one").append("alias: ")
            .append(longValue).append('\n').append(longKey).append(": omitted\n");
        for (int i = 0; i < 30; i++) {
            markdown.append("large-").append(i).append(": ").append(longValue).append('\n');
        }
        markdown.append("---\nInstructions\n");
        service.createDraft("public", "one", null, "1.0.0", skill("one", markdown), null);
        
        Map<String, String> complete = versionFrontMatter("one", "1.0.0");
        assertEquals(1100, complete.get("alias").length());
        assertEquals("omitted", complete.get(longKey));
        Map<String, Object> ext = JacksonUtils.toObj(resources.get("one").getExt(), Map.class);
        Map<String, String> cached = cachedFrontMatter(ext);
        assertTrue(
            JacksonUtils.toJson(cached).getBytes(StandardCharsets.UTF_8).length <= 16 * 1024);
        assertFalse(cached.containsKey(longKey));
        assertEquals(1024, cached.get("alias").length());
        assertTrue(cached.get("alias").endsWith("..."));
        assertEquals(Boolean.TRUE, summary("one").getFrontMatterTruncated());
    }
    
    @Test
    void onlineDraftPublishAndOnlineOfflineFallback() throws Exception {
        create("one", "1.0.0", "Published");
        service.forcePublish("public", "one", "1.0.0", true);
        service.createDraft("public", "one", "1.0.0", "2.0.0", null, null);
        clearInvocations(storage, rows);
        service.updateDraft("public", skill("one", "Unpublished"), null);
        assertEquals("Published", summary("one").getFrontMatter().get("alias"));
        verify(rows, never()).find("public", "one", "skill", "1.0.0");
        verify(storage, never()).get(any());
        service.forcePublish("public", "one", "2.0.0", true);
        assertEquals("Unpublished", summary("one").getFrontMatter().get("alias"));
        service.changeOnlineStatus("public", "one", "version", "2.0.0", false);
        assertEquals("Published", summary("one").getFrontMatter().get("alias"));
        service.changeOnlineStatus("public", "one", "version", "2.0.0", true);
        assertEquals("Unpublished", summary("one").getFrontMatter().get("alias"));
        service.changeOnlineStatus("public", "one", "version", "2.0.0", false);
        service.changeOnlineStatus("public", "one", "version", "1.0.0", false);
        assertNull(summary("one").getFrontMatter());
        verify(storage, never()).get(any());
    }
    
    @Test
    void deletingDraftKeepsOnlineFrontMatter() throws Exception {
        create("one", "1.0.0", "Published");
        service.forcePublish("public", "one", "1.0.0", true);
        service.createDraft("public", "one", "1.0.0", "2.0.0", null, null);
        service.updateDraft("public", skill("one", "Draft"), null);
        service.deleteDraft("public", "one");
        assertEquals("Published", summary("one").getFrontMatter().get("alias"));
    }
    
    @Test
    void legacyVersionDoesNotReadStorageAndUpdateEnablesMetadata() throws Exception {
        create("one", "1.0.0", "Legacy");
        resources.get("one").setExt(null);
        versions.get(key("one", "1.0.0")).setStorage("{\"provider\":\"nacos_config\"}");
        clearInvocations(storage);
        assertNull(summary("one").getFrontMatter());
        service.updateDraft("public", skill("one", "Updated"), null);
        assertEquals("Updated", summary("one").getFrontMatter().get("alias"));
        verify(storage, never()).get(any());
    }
    
    @Test
    void malformedHistoricalMetadataDoesNotFailListOrDetail() throws Exception {
        create("one", "1.0.0", "Legacy");
        create("two", "1.0.0", "Available");
        resources.get("one").setExt("{malformed");
        
        List<SkillSummary> summaries =
            service.listSkills("public", null, null, 1, 100).getPageItems();
        SkillSummary malformed = summaries.stream().filter(item -> "one".equals(item.getName()))
            .findFirst().orElseThrow();
        SkillSummary available = summaries.stream().filter(item -> "two".equals(item.getName()))
            .findFirst().orElseThrow();
        assertNull(malformed.getFrontMatter());
        assertNull(malformed.getFrontMatterTruncated());
        assertEquals("Available", available.getFrontMatter().get("alias"));
        
        SkillMeta detail = service.getSkillDetail("public", "one");
        assertEquals("one", detail.getName());
        assertNull(detail.getFrontMatter());
        assertNull(detail.getFrontMatterTruncated());
    }
    
    @Test
    void historicalPublishDoesNotBackfill() throws Exception {
        create("one", "1.0.0", "Legacy");
        resources.get("one").setExt(null);
        versions.get(key("one", "1.0.0")).setStorage("{\"provider\":\"nacos_config\"}");
        clearInvocations(storage);
        service.forcePublish("public", "one", "1.0.0", true);
        assertNull(summary("one").getFrontMatter());
        verify(storage, never()).get(any());
    }
    
    @Test
    void largeListRejectsMismatchedSnapshotsWithoutExtraQueries() throws Exception {
        for (int i = 0; i < 100; i++) {
            create("skill-" + i, "1.0.0", "Alias " + i);
        }
        resources.get("skill-0").setVersionInfo("{\"labels\":{\"latest\":\"2.0.0\"}}");
        resources.get("skill-1").setExt(null);
        clearInvocations(rows, storage, metas);
        List<SkillSummary> summaries =
            service.listSkills("public", null, null, 1, 100).getPageItems();
        assertEquals(100, summaries.size());
        assertNull(summaries.get(0).getFrontMatter());
        assertNull(summaries.get(0).getFrontMatterTruncated());
        assertNull(summaries.get(1).getFrontMatter());
        assertNull(summaries.get(1).getFrontMatterTruncated());
        assertEquals("Alias 99", summaries.get(99).getFrontMatter().get("alias"));
        verify(metas).list(any(QueryCondition.class), eq(1), eq(100));
        verify(rows, never()).find(anyString(), anyString(), anyString(), anyString());
        verify(storage, never()).get(any());
    }
    
    @Test
    void cacheConflictRecomputesDisplayVersionAndPreservesConcurrentExtensions() throws Exception {
        create("one", "1.0.0", "First");
        service.forcePublish("public", "one", "1.0.0", true);
        service.createDraft("public", "one", "1.0.0", "2.0.0", null, null);
        service.updateDraft("public", skill("one", "Second"), null);
        cacheConflict = () -> {
            AiResource current = resources.get("one");
            current.setVersionInfo("{\"labels\":{\"latest\":\"1.0.0\"},\"onlineCnt\":2}");
            current.setExt("{\"custom\":\"concurrent\"}");
            current.setMetaVersion(current.getMetaVersion() + 1);
        };
        service.forcePublish("public", "one", "2.0.0", true);
        assertEquals("First", summary("one").getFrontMatter().get("alias"));
        assertTrue(resources.get("one").getExt().contains("concurrent"));
    }
    
    @Test
    void exhaustedCacheRetriesDoNotFailCompletedLifecycleOperation() throws Exception {
        create("one", "1.0.0", "First");
        service.forcePublish("public", "one", "1.0.0", true);
        service.createDraft("public", "one", "1.0.0", "2.0.0", null, null);
        service.updateDraft("public", skill("one", "Second"), null);
        rejectCache = true;
        service.forcePublish("public", "one", "2.0.0", true);
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE,
            versions.get(key("one", "2.0.0")).getStatus());
        assertTrue(resources.get("one").getVersionInfo().contains("\"latest\":\"2.0.0\""));
        assertNull(summary("one").getFrontMatter());
        rejectCache = false;
        service.changeOnlineStatus("public", "one", "version", "2.0.0", false);
        service.changeOnlineStatus("public", "one", "version", "2.0.0", true);
        assertEquals("Second", summary("one").getFrontMatter().get("alias"));
    }
    
    @Test
    void redraftKeepsReviewingVersionFrontMatter() throws Exception {
        create("one", "1.0.0", "Reviewed");
        resources.get("one").setVersionInfo("{\"reviewingVersion\":\"1.0.0\"}");
        versions.get(key("one", "1.0.0")).setStatus("reviewed");
        service.redraft("public", "one", "1.0.0");
        assertEquals("Reviewed", summary("one").getFrontMatter().get("alias"));
    }
    
    @Test
    void bootstrapPopulatesNewSkillAndSkipsExistingSkill() throws Exception {
        service.bootstrapSkillFromZip("public", zip("one", "Built in"), "builtin");
        assertEquals("Built in", summary("one").getFrontMatter().get("alias"));
        resources.get("one").setExt(null);
        clearInvocations(rows, storage);
        service.bootstrapSkillFromZip("public", zip("one", "Replacement"), "builtin");
        assertNull(summary("one").getFrontMatter());
        verify(storage, never()).get(any());
        verify(rows, never()).find(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void uploadAndOverwritePopulateFrontMatter() throws Exception {
        service.uploadSkillFromZip(SkillUploadRequest.builder().namespaceId("public")
            .zipBytes(zip("one", "Uploaded")).targetVersion("1.0.0").build());
        assertEquals("Uploaded", summary("one").getFrontMatter().get("alias"));
        service.uploadSkillFromZip(SkillUploadRequest.builder().namespaceId("public")
            .zipBytes(zip("one", "Overwritten")).targetVersion("1.0.0").overwrite(true).build());
        assertEquals("Overwritten", summary("one").getFrontMatter().get("alias"));
    }
    
    private static byte[] zip(String name, String alias) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write(skill(name, alias).getSkillMd().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    
    private void create(String name, String version, String alias) throws Exception {
        service.createDraft("public", name, null, version, skill(name, alias), null);
    }
    
    private SkillSummary summary(String name) throws Exception {
        return service.listSkills("public", null, null, 1, 100).getPageItems().stream()
            .filter(item -> name.equals(item.getName())).findFirst().orElseThrow();
    }
    
    private static Skill skill(String name, String alias) {
        return skill(name, frontMatterHeader(name).append("alias: ").append(alias)
            .append("\n---\nInstructions\n"));
    }
    
    private static Skill skill(String name, StringBuilder markdown) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription("Description");
        skill.setSkillMd(markdown.toString());
        return skill;
    }
    
    private static StringBuilder frontMatterHeader(String name) {
        return new StringBuilder("---\nname: ").append(name)
            .append("\ndescription: Description\n");
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, String> versionFrontMatter(String name, String version) {
        Map<String, Object> descriptor = JacksonUtils.toObj(
            versions.get(key(name, version)).getStorage(), Map.class);
        return (Map<String, String>) descriptor.get("frontMatter");
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, String> cachedFrontMatter(Map<String, Object> ext) {
        return (Map<String, String>) ext.get("frontMatter");
    }
    
    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
    
    private static String key(String name, String version) {
        return name + ":" + version;
    }
    
    private static <T> T copy(T value, Class<T> type) {
        return value == null ? null : JacksonUtils.toObj(JacksonUtils.toJson(value), type);
    }
    
    private static <T> Page<T> page(List<T> items) {
        Page<T> page = new Page<>();
        page.setPageItems(new ArrayList<>(items));
        page.setTotalCount(items.size());
        page.setPagesAvailable(1);
        return page;
    }
}
