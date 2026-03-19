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

package com.alibaba.nacos.ai.storage;

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 9: Storage key naming convention.
 *
 * <p>For any AgentSpec with name N and version V, the storage group SHALL be
 * {@code agentspec__{N}__{V}}, the main dataId SHALL be {@code manifest.json},
 * and each AgentSpecResource SHALL use its original file path as the dataId.</p>
 *
 * <p><b>Validates: Requirements 7.1, 7.2, 7.3</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecStorageKeyPropertyTest {

    private static final String PROVIDER = NacosConfigAiResourceStorage.TYPE;

    /**
     * Property 9a: AgentSpec storage group follows {@code agentspec__{name}__{version}} format.
     *
     * <p>For any valid namespace, name, and version, building a typed AgentSpec storage key
     * and parsing it SHALL produce a group equal to {@code agentspec__{name}__{version}}.</p>
     */
    @Property(tries = 50)
    void agentSpecGroupFollowsNamingConvention(
            @ForAll("validNamespaceIds") String namespaceId,
            @ForAll("validNames") String name,
            @ForAll("validVersions") String version) {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                PROVIDER, namespaceId,
                NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                name, version, AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID);
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);

        String expectedGroup = AgentSpecUtils.AGENTSPEC_GROUP_PREFIX + name + "__" + version;
        assertEquals(expectedGroup, parts.group(),
                "Group must follow agentspec__{name}__{version} format");
        assertTrue(parts.group().startsWith(AgentSpecUtils.AGENTSPEC_GROUP_PREFIX),
                "Group must start with agentspec__ prefix");
    }

    /**
     * Property 9b: AgentSpec main dataId is always {@code manifest.json}.
     *
     * <p>For any valid namespace, name, and version, building a storage key with the
     * AgentSpec main file path SHALL produce a dataId equal to {@code manifest.json}.</p>
     */
    @Property(tries = 50)
    void agentSpecMainDataIdIsManifestJson(
            @ForAll("validNamespaceIds") String namespaceId,
            @ForAll("validNames") String name,
            @ForAll("validVersions") String version) {
        String mainFilePath = NacosConfigAiResourceStorage.getMainFilePath(
                AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID);
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                PROVIDER, namespaceId,
                NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                name, version, mainFilePath);
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);

        assertEquals("manifest.json", parts.dataId(),
                "Main dataId must be manifest.json");
    }

    /**
     * Property 9c: AgentSpec resource dataId preserves original file path encoding.
     *
     * <p>For any valid resource type and resource name, the AgentSpec resource file path
     * helper SHALL produce a dataId that starts with the resource prefix and ends with
     * the resource suffix, and parsing the full storage key SHALL yield that dataId.</p>
     */
    @Property(tries = 50)
    void agentSpecResourceDataIdPreservesFilePath(
            @ForAll("validNamespaceIds") String namespaceId,
            @ForAll("validNames") String agentSpecName,
            @ForAll("validVersions") String version,
            @ForAll("validResourceTypes") String resourceType,
            @ForAll("validResourceNames") String resourceName) {
        String resourceFilePath = NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(
                resourceType, resourceName);
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                PROVIDER, namespaceId,
                NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                agentSpecName, version, resourceFilePath);
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);

        assertEquals(resourceFilePath, parts.dataId(),
                "Resource dataId must match the generated resource file path");
        assertTrue(parts.dataId().startsWith(AgentSpecUtils.RESOURCE_DATA_ID_PREFIX),
                "Resource dataId must start with resource_ prefix");
        assertTrue(parts.dataId().endsWith(AgentSpecUtils.RESOURCE_DATA_ID_SUFFIX),
                "Resource dataId must end with .json suffix");
    }

    /**
     * Property 9d: AgentSpec group prefix is distinct from Skill group prefix.
     *
     * <p>For any name and version, the AgentSpec storage key group SHALL start with
     * {@code agentspec__} and NOT with {@code skill_}.</p>
     */
    @Property(tries = 30)
    void agentSpecGroupPrefixIsDistinctFromSkill(
            @ForAll("validNamespaceIds") String namespaceId,
            @ForAll("validNames") String name,
            @ForAll("validVersions") String version) {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                PROVIDER, namespaceId,
                NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                name, version, AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID);
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);

        assertTrue(parts.group().startsWith("agentspec__"),
                "AgentSpec group must start with agentspec__");
        assertTrue(!parts.group().startsWith("skill_") || parts.group().startsWith("agentspec__"),
                "AgentSpec group must not use skill_ prefix");
    }

    // ---- Arbitraries ----

    @Provide
    Arbitrary<String> validNamespaceIds() {
        return Arbitraries.of("public", "test-ns", "ns-1", "namespace_abc");
    }

    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings().alpha().numeric().withChars('-', '_')
                .ofMinLength(1).ofMaxLength(30)
                .filter(s -> !s.isBlank() && !s.contains(":"));
    }

    @Provide
    Arbitrary<String> validVersions() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 100),
                Arbitraries.integers().between(0, 99),
                Arbitraries.integers().between(0, 99)
        ).as((major, minor, patch) -> major + "." + minor + "." + patch);
    }

    @Provide
    Arbitrary<String> validResourceTypes() {
        return Arbitraries.of("config", "skill", "cron", "dockerfile", "other");
    }

    @Provide
    Arbitrary<String> validResourceNames() {
        return Arbitraries.of("SOUL.md", "AGENTS.md", "MEMORY.md", "jobs.json",
                "my-skill.md", "Dockerfile", "tool-analysis.json");
    }
}
