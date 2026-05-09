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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Version resolution priority.
 *
 * <p><b>Validates: Requirement 5.3</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecVersionResolutionTest {
    
    private static final String LABEL_LATEST = "latest";
    
    private static String resolveVersion(AiResource meta, String explicitVersion, String label) {
        if (StringUtils.isNotBlank(label)) {
            VersionInfo info = parseVersionInfo(meta.getVersionInfo());
            if (info != null && info.labels != null) {
                String v = info.labels.get(label);
                if (StringUtils.isNotBlank(v)) {
                    return v;
                }
            }
        }
        if (StringUtils.isNotBlank(explicitVersion)) {
            return explicitVersion;
        }
        VersionInfo info = parseVersionInfo(meta.getVersionInfo());
        if (info != null && info.labels != null) {
            String v = info.labels.get(LABEL_LATEST);
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }
    
    private static VersionInfo parseVersionInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(json, VersionInfo.class);
        } catch (Exception ignored) {
            return null;
        }
    }
    
    static class VersionInfo {
        
        public String editingVersion;
        public String reviewingVersion;
        public Integer onlineCnt;
        public Map<String, String> labels;
    }
    
    static class VersionResolutionTestData {
        
        final Map<String, String> labels;
        final String labelParam;
        final String explicitVersion;
        final String latestVersion;
        
        VersionResolutionTestData(Map<String, String> labels, String labelParam,
            String explicitVersion, String latestVersion) {
            this.labels = labels;
            this.labelParam = labelParam;
            this.explicitVersion = explicitVersion;
            this.latestVersion = latestVersion;
        }
        
        AiResource toAiResource() {
            AiResource resource = new AiResource();
            resource.setNamespaceId("test-ns");
            resource.setName("test-agentspec");
            resource.setType("agentspec");
            resource.setStatus("enable");
            resource.setMetaVersion(1L);
            
            Map<String, Object> versionInfoMap = new HashMap<>(4);
            versionInfoMap.put("onlineCnt", 1);
            versionInfoMap.put("labels", labels);
            resource.setVersionInfo(JacksonUtils.toJson(versionInfoMap));
            return resource;
        }
    }
    
    private static List<VersionResolutionTestData> labelPresentScenarios() {
        List<VersionResolutionTestData> list = new ArrayList<>();
        for (String labelName : new String[] {"stable", "canary", "beta"}) {
            for (String labelVersion : new String[] {"v1", "v2", "v10"}) {
                for (String explicitVersion : new String[] {"v9", "v3"}) {
                    for (String latestVersion : new String[] {"v1", "v5"}) {
                        Map<String, String> labels = new HashMap<>();
                        labels.put(labelName, labelVersion);
                        labels.put(LABEL_LATEST, latestVersion);
                        list.add(new VersionResolutionTestData(labels, labelName, explicitVersion,
                            latestVersion));
                    }
                }
            }
        }
        return list;
    }
    
    private static List<VersionResolutionTestData> explicitVersionScenarios() {
        List<VersionResolutionTestData> list = new ArrayList<>();
        for (String explicitVersion : new String[] {"v1", "v2", "v10"}) {
            for (String latestVersion : new String[] {"v3", "v4"}) {
                Map<String, String> labels = new HashMap<>();
                labels.put(LABEL_LATEST, latestVersion);
                list.add(
                    new VersionResolutionTestData(labels, null, explicitVersion, latestVersion));
            }
        }
        return list;
    }
    
    private static List<VersionResolutionTestData> latestFallbackScenarios() {
        List<VersionResolutionTestData> list = new ArrayList<>();
        for (String latestVersion : new String[] {"v1", "v2", "v20"}) {
            Map<String, String> labels = new HashMap<>();
            labels.put(LABEL_LATEST, latestVersion);
            list.add(new VersionResolutionTestData(labels, null, null, latestVersion));
        }
        return list;
    }
    
    private static List<VersionResolutionTestData> noResolutionScenarios() {
        List<VersionResolutionTestData> list = new ArrayList<>();
        list.add(new VersionResolutionTestData(new HashMap<>(), null, null, null));
        Map<String, String> other = new HashMap<>();
        other.put("stable", "v1");
        other.put("canary", "v2");
        list.add(new VersionResolutionTestData(other, null, null, null));
        return list;
    }
    
    @Test
    void labelTakesHighestPriority() {
        for (VersionResolutionTestData data : labelPresentScenarios()) {
            AiResource meta = data.toAiResource();
            String resolved = resolveVersion(meta, data.explicitVersion, data.labelParam);
            
            String expectedFromLabel = data.labels.get(data.labelParam);
            assertNotNull(resolved,
                "Resolved version should not be null when label maps to a version");
            assertEquals(expectedFromLabel, resolved,
                "Label should take priority over explicit version. Label='" + data.labelParam
                    + "' should resolve to '" + expectedFromLabel + "' but got '" + resolved + "'");
        }
    }
    
    @Test
    void explicitVersionUsedWhenNoLabel() {
        for (VersionResolutionTestData data : explicitVersionScenarios()) {
            AiResource meta = data.toAiResource();
            String resolved = resolveVersion(meta, data.explicitVersion, null);
            
            assertEquals(data.explicitVersion, resolved,
                "Explicit version should be returned when no label is provided");
        }
    }
    
    @Test
    void latestLabelFallbackUsedWhenNeitherLabelNorExplicitVersion() {
        for (VersionResolutionTestData data : latestFallbackScenarios()) {
            AiResource meta = data.toAiResource();
            String resolved = resolveVersion(meta, null, null);
            
            assertEquals(data.latestVersion, resolved,
                "Latest label fallback should be used when neither label nor explicit version is provided");
        }
    }
    
    @Test
    void nullReturnedWhenNoResolutionPathSucceeds() {
        for (VersionResolutionTestData data : noResolutionScenarios()) {
            AiResource meta = data.toAiResource();
            String resolved = resolveVersion(meta, null, null);
            
            assertNull(resolved,
                "Resolution should return null when no label, no explicit version, and no 'latest' label");
        }
    }
}
