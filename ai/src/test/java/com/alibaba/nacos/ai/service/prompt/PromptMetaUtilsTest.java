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

import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptMetaUtilsTest {
    
    @Test
    void normalizeMetaShouldInitCollectionsWhenNullFields() {
        PromptMetaInfo meta = new PromptMetaInfo();
        PromptMetaInfo normalized = PromptMetaUtils.normalizeMeta(meta);
        assertNotNull(normalized.getLabels());
        assertNotNull(normalized.getVersions());
        assertNotNull(normalized.getBizTags());
    }
    
    @Test
    void cloneMetaShouldDeepCopyCollections() {
        PromptMetaInfo meta = new PromptMetaInfo();
        meta.setPromptKey("p1");
        meta.setLabels(new HashMap<>());
        meta.getLabels().put("prod", "1.0.0");
        meta.setVersions(new ArrayList<>(List.of("1.0.0")));
        meta.setBizTags(new ArrayList<>(List.of("finance")));
        
        PromptMetaInfo copied = PromptMetaUtils.cloneMeta(meta);
        copied.getLabels().put("gray", "1.0.1");
        copied.getVersions().add("1.0.1");
        copied.getBizTags().add("ops");
        
        assertEquals(1, meta.getLabels().size());
        assertEquals(1, meta.getVersions().size());
        assertEquals(1, meta.getBizTags().size());
    }
    
    @Test
    void resolveTargetVersionShouldPreferVersionOverLabel() throws NacosException {
        PromptMetaInfo meta = new PromptMetaInfo();
        meta.setLabels(new HashMap<>());
        meta.getLabels().put("prod", "2.0.0");
        meta.setVersions(new ArrayList<>(List.of("1.0.0", "2.0.0")));
        meta.setLatestVersion("2.0.0");
        String actual = PromptMetaUtils.resolveTargetVersion(meta, "1.0.0", "prod");
        assertEquals("1.0.0", actual);
    }
    
    @Test
    void resolveTargetVersionShouldThrowWhenLabelNotFound() {
        PromptMetaInfo meta = new PromptMetaInfo();
        meta.setLabels(new HashMap<>());
        meta.setVersions(new ArrayList<>(List.of("1.0.0")));
        meta.setLatestVersion("1.0.0");
        assertThrows(NacosException.class, () -> PromptMetaUtils.resolveTargetVersion(meta, null, "prod"));
    }
    
    @Test
    void resolveTargetVersionShouldThrowWhenVersionInvalid() {
        PromptMetaInfo meta = new PromptMetaInfo();
        meta.setLabels(new HashMap<>());
        meta.setVersions(new ArrayList<>(List.of("1.0.0")));
        meta.setLatestVersion("1.0.0");
        assertThrows(NacosException.class, () -> PromptMetaUtils.resolveTargetVersion(meta, "1.0", null));
    }
    
    @Test
    void resolveTargetVersionShouldFallbackToLatestOrThrow() throws NacosException {
        PromptMetaInfo meta = new PromptMetaInfo();
        meta.setLabels(new HashMap<>());
        meta.setVersions(new ArrayList<>(List.of("1.0.0")));
        meta.setLatestVersion("1.0.0");
        assertEquals("1.0.0", PromptMetaUtils.resolveTargetVersion(meta, null, null));
        
        meta.setLatestVersion(null);
        NacosException ex = assertThrows(NacosException.class, () -> PromptMetaUtils.resolveTargetVersion(meta, null, null));
        assertTrue(ex.getErrMsg().contains("latest version"));
    }
}
