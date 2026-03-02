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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.ai.utils.PromptDataIdUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptAdminInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptLabelVersionMapping;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptAdminOperationServiceImplTest {
    
    private static final String PROMPT_GROUP = "nacos-ai-prompt";
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigDetailService configDetailService;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    private PromptAdminOperationServiceImpl service;
    
    @BeforeEach
    void setUp() {
        service = new PromptAdminOperationServiceImpl(configOperationService, configDetailService, configInfoPersistService);
    }
    
    @Test
    void publishPromptVersionShouldPublishVersionMetaAndLatestWhenFirstPublish() throws NacosException {
        String ns = "public";
        String key = "p1";
        String version = "1.0.0";
        String mappingDataId = PromptDataIdUtils.buildLabelVersionMappingDataId(key);
        String versionDataId = PromptDataIdUtils.buildVersionDataId(key, version);
        
        when(configInfoPersistService.findConfigInfo(mappingDataId, PROMPT_GROUP, ns)).thenReturn(null);
        ConfigInfoWrapper latestSource = new ConfigInfoWrapper();
        latestSource.setContent("{\"version\":\"1.0.0\",\"template\":\"hello\"}");
        when(configInfoPersistService.findConfigInfo(versionDataId, PROMPT_GROUP, ns))
                .thenReturn(null)
                .thenReturn(latestSource);
        
        boolean result = service.publishPromptVersion(ns, key, version, "hello", "c1", "desc",
                List.of("a", "a", "b"), "u1", "127.0.0.1");
        
        assertTrue(result);
        ArgumentCaptor<ConfigForm> formCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        ArgumentCaptor<ConfigRequestInfo> reqCaptor = ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(configOperationService, atLeastOnce()).publishConfig(formCaptor.capture(), reqCaptor.capture(), eq(null));
        List<ConfigForm> forms = formCaptor.getAllValues();
        assertEquals(4, forms.size());
        assertTrue(forms.stream().anyMatch(f -> PromptDataIdUtils.buildVersionDataId(key, version).equals(f.getDataId())));
        assertTrue(forms.stream().anyMatch(f -> PromptDataIdUtils.buildLabelVersionMappingDataId(key).equals(f.getDataId())));
        assertTrue(forms.stream().anyMatch(f -> PromptDataIdUtils.buildAdminInfoDataId(key).equals(f.getDataId())));
        assertTrue(forms.stream().anyMatch(f -> PromptDataIdUtils.buildLatestDataId(key).equals(f.getDataId())));
    }
    
    @Test
    void publishPromptVersionShouldRejectDescriptionOrBizTagsWhenNotFirstPublish() {
        String ns = "public";
        String key = "p1";
        String version = "1.1.0";
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey(key);
        mapping.setVersions(new ArrayList<>(List.of("1.0.0")));
        mapping.setLabels(new HashMap<>());
        ConfigInfoWrapper metaConfig = new ConfigInfoWrapper();
        metaConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        metaConfig.setMd5("m1");
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(metaConfig);
        
        assertThrows(NacosException.class,
                () -> service.publishPromptVersion(ns, key, version, "hello", null, "desc", null, "u1", "127.0.0.1"));
    }
    
    @Test
    void bindLabelShouldThrowNotFoundWhenVersionNotExists() {
        String ns = "public";
        String key = "p1";
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey(key);
        mapping.setVersions(new ArrayList<>(List.of("1.0.0")));
        mapping.setLabels(new HashMap<>());
        ConfigInfoWrapper metaConfig = new ConfigInfoWrapper();
        metaConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        metaConfig.setMd5("m1");
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(metaConfig);
        
        assertThrows(NacosException.class, () -> service.bindLabel(ns, key, "prod", "2.0.0", "u1", "127.0.0.1"));
    }
    
    @Test
    void queryPromptDetailShouldResolveByLabel() throws NacosException {
        String ns = "public";
        String key = "p1";
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey(key);
        mapping.setVersions(new ArrayList<>(List.of("1.0.0")));
        mapping.setLabels(new HashMap<>());
        mapping.getLabels().put("prod", "1.0.0");
        mapping.setLatestVersion("1.0.0");
        ConfigInfoWrapper metaConfig = new ConfigInfoWrapper();
        metaConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(metaConfig);
        
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setVersion("1.0.0");
        versionInfo.setTemplate("hello");
        ConfigAllInfo allInfo = new ConfigAllInfo();
        allInfo.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(versionInfo));
        allInfo.setMd5("m1");
        allInfo.setCreateUser("u1");
        when(configInfoPersistService.findConfigAllInfo(PromptDataIdUtils.buildVersionDataId(key, "1.0.0"), PROMPT_GROUP, ns))
                .thenReturn(allInfo);
        
        PromptVersionInfo actual = service.queryPromptDetail(ns, key, null, "prod");
        
        assertEquals(key, actual.getPromptKey());
        assertEquals("1.0.0", actual.getVersion());
        assertEquals("m1", actual.getMd5());
        assertEquals("u1", actual.getSrcUser());
    }
    
    @Test
    void listPromptVersionsShouldSortBySemverDescAndPaginate() throws NacosException {
        String ns = "public";
        String key = "p1";
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey(key);
        mapping.setVersions(new ArrayList<>(List.of("1.0.0", "2.0.0")));
        mapping.setLabels(new HashMap<>());
        ConfigInfoWrapper metaConfig = new ConfigInfoWrapper();
        metaConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(metaConfig);
        
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setVersion("2.0.0");
        versionInfo.setCommitMsg("msg");
        ConfigAllInfo allInfo = new ConfigAllInfo();
        allInfo.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(versionInfo));
        allInfo.setCreateUser("u1");
        when(configInfoPersistService.findConfigAllInfo(PromptDataIdUtils.buildVersionDataId(key, "2.0.0"), PROMPT_GROUP, ns))
                .thenReturn(allInfo);
        
        List<PromptVersionSummary> items = service.listPromptVersions(ns, key, 1, 1).getPageItems();
        
        assertEquals(1, items.size());
        assertEquals("2.0.0", items.get(0).getVersion());
    }
    
    @Test
    void publishPromptVersionShouldThrowWhenVersionInvalid() {
        assertThrows(NacosException.class, () -> service.publishPromptVersion(
                "public", "p1", "1.0", "hello", null, null, null, "u1", "127.0.0.1"));
    }
    
    @Test
    void publishPromptVersionShouldThrowWhenTemplateBlank() {
        assertThrows(NacosException.class, () -> service.publishPromptVersion(
                "public", "p1", "1.0.0", " ", null, null, null, "u1", "127.0.0.1"));
    }
    
    @Test
    void publishPromptVersionShouldThrowConflictWhenMetaContainsVersion() {
        String ns = "public";
        String key = "p1";
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey(key);
        mapping.setVersions(new ArrayList<>(List.of("1.0.0")));
        mapping.setLabels(new HashMap<>());
        ConfigInfoWrapper metaConfig = new ConfigInfoWrapper();
        metaConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(metaConfig);
        
        assertThrows(NacosException.class, () -> service.publishPromptVersion(
                ns, key, "1.0.0", "hello", null, null, null, "u1", "127.0.0.1"));
    }
    
    @Test
    void publishPromptVersionShouldThrowConflictWhenVersionConfigAlreadyExists() {
        String ns = "public";
        String key = "p1";
        String version = "1.0.0";
        ConfigInfoWrapper existedVersion = new ConfigInfoWrapper();
        existedVersion.setContent("exists");
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(null);
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildVersionDataId(key, version), PROMPT_GROUP, ns))
                .thenReturn(existedVersion);
        
        assertThrows(NacosException.class, () -> service.publishPromptVersion(
                ns, key, version, "hello", null, null, null, "u1", "127.0.0.1"));
    }
    
    @Test
    void bindLabelShouldPublishMetaWhenSuccess() throws NacosException {
        String ns = "public";
        String key = "p1";
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey(key);
        mapping.setVersions(new ArrayList<>(List.of("1.0.0")));
        mapping.setLabels(new HashMap<>());
        ConfigInfoWrapper metaConfig = new ConfigInfoWrapper();
        metaConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        metaConfig.setMd5("m1");
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(metaConfig);
        
        service.bindLabel(ns, key, "prod", "1.0.0", "u1", "127.0.0.1");
        
        ArgumentCaptor<ConfigForm> formCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService).publishConfig(formCaptor.capture(), any(ConfigRequestInfo.class), eq(null));
        assertEquals(PromptDataIdUtils.buildLabelVersionMappingDataId(key), formCaptor.getValue().getDataId());
    }
    
    @Test
    void unbindLabelShouldPublishMetaWhenSuccess() throws NacosException {
        String ns = "public";
        String key = "p1";
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey(key);
        mapping.setVersions(new ArrayList<>(List.of("1.0.0")));
        mapping.setLabels(new HashMap<>());
        mapping.getLabels().put("prod", "1.0.0");
        ConfigInfoWrapper metaConfig = new ConfigInfoWrapper();
        metaConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        metaConfig.setMd5("m1");
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(metaConfig);
        
        service.unbindLabel(ns, key, "prod", "u1", "127.0.0.1");
        
        ArgumentCaptor<ConfigForm> formCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService).publishConfig(formCaptor.capture(), any(ConfigRequestInfo.class), eq(null));
        PromptLabelVersionMapping written =
                com.alibaba.nacos.common.utils.JacksonUtils.toObj(formCaptor.getValue().getContent(),
                        PromptLabelVersionMapping.class);
        assertNull(written.getLabels().get("prod"));
    }
    
    @Test
    void deletePromptShouldDeleteMetaLatestAndAllVersionConfigs() throws NacosException {
        String ns = "public";
        String key = "p1";
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey(key);
        mapping.setVersions(new ArrayList<>(List.of("1.0.0", "2.0.0")));
        mapping.setLabels(new HashMap<>());
        ConfigInfoWrapper metaConfig = new ConfigInfoWrapper();
        metaConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(metaConfig);
        
        service.deletePrompt(ns, key, "u1", "127.0.0.1");
        
        verify(configOperationService, times(5)).deleteConfig(anyString(), eq(PROMPT_GROUP), eq(ns), eq(null), eq("127.0.0.1"),
                eq("u1"), eq(null));
    }
    
    @Test
    void updatePromptMetadataShouldPublishMergedMeta() throws NacosException {
        String ns = "public";
        String key = "p1";
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey(key);
        mapping.setVersions(new ArrayList<>(List.of("1.0.0")));
        mapping.setLabels(new HashMap<>());
        PromptAdminInfo adminInfo = new PromptAdminInfo();
        adminInfo.setPromptKey(key);
        adminInfo.setBizTags(new ArrayList<>(List.of("old")));
        adminInfo.setDescription("old");
        ConfigInfoWrapper metaConfig = new ConfigInfoWrapper();
        metaConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        ConfigInfoWrapper adminConfig = new ConfigInfoWrapper();
        adminConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(adminInfo));
        metaConfig.setMd5("m1");
        adminConfig.setMd5("m2");
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(metaConfig);
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildAdminInfoDataId(key), PROMPT_GROUP, ns))
                .thenReturn(adminConfig);
        
        service.updatePromptMetadata(ns, key, "new", List.of("a", "b"), "u1", "127.0.0.1");
        
        ArgumentCaptor<ConfigForm> formCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService).publishConfig(formCaptor.capture(), any(ConfigRequestInfo.class), eq(null));
        assertEquals(PromptDataIdUtils.buildAdminInfoDataId(key), formCaptor.getValue().getDataId());
        PromptAdminInfo written =
                com.alibaba.nacos.common.utils.JacksonUtils.toObj(formCaptor.getValue().getContent(), PromptAdminInfo.class);
        assertEquals("new", written.getDescription());
        assertEquals(2, written.getBizTags().size());
    }
    
    @Test
    void listPromptsShouldUseMetaPatternAndFilterInvalidJson() throws NacosException {
        String ns = "public";
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(1);
        page.setPagesAvailable(1);
        page.setTotalCount(2);
        ConfigInfo ok = new ConfigInfo();
        ok.setDataId("p1.admin-info.json");
        PromptAdminInfo adminInfo = new PromptAdminInfo();
        adminInfo.setPromptKey("p1");
        adminInfo.setBizTags(new ArrayList<>(List.of("x")));
        ok.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(adminInfo));
        ConfigInfo bad = new ConfigInfo();
        bad.setDataId("p2.admin-info.json");
        bad.setContent("not-json");
        page.setPageItems(List.of(ok, bad));
        when(configDetailService.findConfigInfoPage(eq("blur"), eq(1), eq(10), eq("*p*.admin-info.json"), eq(PROMPT_GROUP), eq(ns),
                eq(null))).thenReturn(page);
        ConfigInfoWrapper mappingConfig = new ConfigInfoWrapper();
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey("p1");
        mapping.setLatestVersion("1.0.0");
        mappingConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId("p1"), PROMPT_GROUP, ns))
                .thenReturn(mappingConfig);
        
        Page<?> actual = service.listPrompts(ns, "p", "blur", null, 1, 10);
        assertEquals(1, actual.getPageItems().size());
    }
    
    @Test
    void queryPromptDetailShouldThrowWhenVersionConfigMissing() {
        String ns = "public";
        String key = "p1";
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setPromptKey(key);
        mapping.setVersions(new ArrayList<>(List.of("1.0.0")));
        mapping.setLabels(new HashMap<>());
        mapping.setLatestVersion("1.0.0");
        ConfigInfoWrapper metaConfig = new ConfigInfoWrapper();
        metaConfig.setContent(com.alibaba.nacos.common.utils.JacksonUtils.toJson(mapping));
        when(configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildLabelVersionMappingDataId(key), PROMPT_GROUP, ns))
                .thenReturn(metaConfig);
        when(configInfoPersistService.findConfigAllInfo(PromptDataIdUtils.buildVersionDataId(key, "1.0.0"), PROMPT_GROUP, ns))
                .thenReturn(null);
        
        assertThrows(NacosException.class, () -> service.queryPromptDetail(ns, key, null, null));
    }
}
