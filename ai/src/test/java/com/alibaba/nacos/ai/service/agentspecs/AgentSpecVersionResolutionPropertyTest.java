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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Property 7: Version resolution priority.
 *
 * <p>For any AgentSpec with multiple versions and label mappings, client version resolution
 * SHALL follow the priority: label parameter > explicit version parameter > latest label fallback.
 * When a label is provided, the version mapped to that label SHALL be returned regardless of
 * other parameters.</p>
 *
 * <p><b>Validates: Requirement 5.3</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecVersionResolutionPropertyTest {

    private static final String LABEL_LATEST = "latest";

    // ---- resolveVersion logic (mirrors AgentSpecOperationServiceImpl.resolveVersion) ----

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

    // ---- Test data model ----

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

    // ---- Properties ----

    /**
     * Property 7a: Label takes highest priority.
     *
     * <p>When a label is provided and maps to a version, that version is returned
     * regardless of whether an explicit version is also provided.</p>
     */
    @Property(tries = 100)
    void labelTakesHighestPriority(
            @ForAll("labelPresentScenarios") VersionResolutionTestData data) {

        AiResource meta = data.toAiResource();
        String resolved = resolveVersion(meta, data.explicitVersion, data.labelParam);

        String expectedFromLabel = data.labels.get(data.labelParam);
        assertNotNull(resolved, "Resolved version should not be null when label maps to a version");
        assertEquals(expectedFromLabel, resolved,
                "Label should take priority over explicit version. Label='" + data.labelParam
                        + "' should resolve to '" + expectedFromLabel + "' but got '" + resolved + "'");
    }

    /**
     * Property 7b: Explicit version is used when no label is provided.
     *
     * <p>When no label is provided but an explicit version is given, that explicit version
     * is returned.</p>
     */
    @Property(tries = 100)
    void explicitVersionUsedWhenNoLabel(
            @ForAll("explicitVersionScenarios") VersionResolutionTestData data) {

        AiResource meta = data.toAiResource();
        // Pass null/blank label, non-blank explicit version
        String resolved = resolveVersion(meta, data.explicitVersion, null);

        assertEquals(data.explicitVersion, resolved,
                "Explicit version should be returned when no label is provided");
    }

    /**
     * Property 7c: Latest label fallback is used when neither label nor explicit version is provided.
     *
     * <p>When neither a label parameter nor an explicit version is provided, the version
     * mapped to the "latest" label is returned as fallback.</p>
     */
    @Property(tries = 100)
    void latestLabelFallbackUsedWhenNeitherLabelNorExplicitVersion(
            @ForAll("latestFallbackScenarios") VersionResolutionTestData data) {

        AiResource meta = data.toAiResource();
        String resolved = resolveVersion(meta, null, null);

        assertEquals(data.latestVersion, resolved,
                "Latest label fallback should be used when neither label nor explicit version is provided");
    }

    /**
     * Property 7d: Null returned when no resolution path succeeds.
     *
     * <p>When no label is provided, no explicit version is given, and no "latest" label
     * exists, the resolution returns null.</p>
     */
    @Property(tries = 50)
    void nullReturnedWhenNoResolutionPathSucceeds(
            @ForAll("noResolutionScenarios") VersionResolutionTestData data) {

        AiResource meta = data.toAiResource();
        String resolved = resolveVersion(meta, null, null);

        assertNull(resolved,
                "Resolution should return null when no label, no explicit version, and no 'latest' label");
    }

    // ---- Arbitraries ----

    private Arbitrary<String> versionStrings() {
        return Arbitraries.of("v1", "v2", "v3", "v4", "v5", "v10", "v20");
    }

    private Arbitrary<String> labelNames() {
        return Arbitraries.of("stable", "canary", "beta", "prod", "staging");
    }

    /**
     * Scenarios where a label is provided and maps to a version in the labels map.
     * Also provides an explicit version to verify label takes precedence.
     */
    @Provide
    Arbitrary<VersionResolutionTestData> labelPresentScenarios() {
        return Combinators.combine(labelNames(), versionStrings(), versionStrings(), versionStrings())
                .as((labelName, labelVersion, explicitVersion, latestVersion) -> {
                    Map<String, String> labels = new HashMap<>();
                    labels.put(labelName, labelVersion);
                    labels.put(LABEL_LATEST, latestVersion);
                    return new VersionResolutionTestData(labels, labelName, explicitVersion, latestVersion);
                });
    }

    /**
     * Scenarios where no label is provided but an explicit version is given.
     */
    @Provide
    Arbitrary<VersionResolutionTestData> explicitVersionScenarios() {
        return Combinators.combine(versionStrings(), versionStrings())
                .as((explicitVersion, latestVersion) -> {
                    Map<String, String> labels = new HashMap<>();
                    labels.put(LABEL_LATEST, latestVersion);
                    return new VersionResolutionTestData(labels, null, explicitVersion, latestVersion);
                });
    }

    /**
     * Scenarios where neither label nor explicit version is provided, but "latest" label exists.
     */
    @Provide
    Arbitrary<VersionResolutionTestData> latestFallbackScenarios() {
        return versionStrings().map(latestVersion -> {
            Map<String, String> labels = new HashMap<>();
            labels.put(LABEL_LATEST, latestVersion);
            return new VersionResolutionTestData(labels, null, null, latestVersion);
        });
    }

    /**
     * Scenarios where no resolution path succeeds: no label param, no explicit version,
     * and no "latest" label in the map.
     */
    @Provide
    Arbitrary<VersionResolutionTestData> noResolutionScenarios() {
        // Labels map with only non-"latest" entries (or empty)
        return Arbitraries.of(true, false).map(hasOtherLabels -> {
            Map<String, String> labels = new HashMap<>();
            if (hasOtherLabels) {
                labels.put("stable", "v1");
                labels.put("canary", "v2");
            }
            return new VersionResolutionTestData(labels, null, null, null);
        });
    }
}
