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

package com.alibaba.nacos.api.ai.model.agentspecs;

import com.alibaba.nacos.api.ai.model.NacosAiConfigKeyCodec;
import com.alibaba.nacos.api.utils.StringUtils;

/**
 * Utility class for AgentSpec operations.
 * Mirrors {@link com.alibaba.nacos.api.ai.model.skills.SkillUtils} patterns with AgentSpec-specific constants.
 *
 * @author nacos
 */
public class AgentSpecUtils {
    
    /**
     * Main config dataId for AgentSpec (manifest.json).
     */
    public static final String AGENTSPEC_MAIN_DATA_ID = "manifest.json";
    
    /**
     * Resource config dataId prefix.
     */
    public static final String RESOURCE_DATA_ID_PREFIX = "resource_";
    
    /**
     * Resource config dataId suffix.
     */
    public static final String RESOURCE_DATA_ID_SUFFIX = ".json";
    
    /**
     * AgentSpec index config dataId for client-side config caching.
     * Server writes a manifest config with this dataId at group {@code agentspec__{name}}
     * containing the current online version and file list.
     */
    public static final String AGENTSPEC_INDEX_DATA_ID = "agentspec_index.json";
    
    /**
     * AgentSpec group prefix.
     */
    public static final String AGENTSPEC_GROUP_PREFIX = "agentspec__";
    
    private static final String DOUBLE_UNDERSCORE = "__";
    private static final String FILE_EXTENSION_PATTERN = ".*\\.[a-zA-Z0-9]+$";
    
    /**
     * Configuration info containing dataId and group.
     */
    public static class ConfigInfo {
        
        private final String dataId;
        
        private final String group;
        
        public ConfigInfo(String dataId, String group) {
            this.dataId = dataId;
            this.group = group;
        }
        
        public String getDataId() {
            return dataId;
        }
        
        public String getGroup() {
            return group;
        }
    }
    
    /**
     * Generate resource ID from resource type and name.
     * Format: {type}_{resourcename}
     * If resourcename ends with .xx, convert the last . to __
     * Slashes in type are encoded as dots so that dataId (resource_{resourceId}.json) is valid in Nacos.
     *
     * @param type resource type (can be null or empty; may contain / for multi-level paths)
     * @param resourceName resource name
     * @return resource ID (safe for use in config dataId)
     */
    public static String generateResourceId(String type, String resourceName) {
        if (resourceName == null || resourceName.trim().isEmpty()) {
            return "";
        }
        
        // If resourcename ends with .xx, convert the last . to __
        String processedName = resourceName;
        if (resourceName.matches(FILE_EXTENSION_PATTERN)) {
            int lastDotIndex = resourceName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                processedName = resourceName.substring(0, lastDotIndex) + DOUBLE_UNDERSCORE
                    + resourceName.substring(lastDotIndex + 1);
            }
        }
        
        if (type != null && !type.trim().isEmpty()) {
            String safeType = type.trim().replace("/", ".");
            return safeType + "_" + processedName;
        } else {
            return processedName;
        }
    }
    
    /**
     * Build AgentSpec main config info (dataId and group).
     *
     * @param agentSpecName name of AgentSpec
     * @return ConfigInfo containing dataId and group
     * @throws IllegalArgumentException if agentSpecName is blank
     */
    public static ConfigInfo buildAgentSpecMainConfigInfo(String agentSpecName) {
        if (StringUtils.isBlank(agentSpecName)) {
            throw new IllegalArgumentException("AgentSpec name cannot be blank");
        }
        return new ConfigInfo(AGENTSPEC_MAIN_DATA_ID, buildAgentSpecGroup(agentSpecName));
    }
    
    /**
     * Build the Nacos Config group for an AgentSpec (no version suffix).
     *
     * @param agentSpecName name of AgentSpec
     * @return config group string, e.g. "agentspec__myworker"
     * @throws IllegalArgumentException if agentSpecName is blank
     */
    public static String buildAgentSpecGroup(String agentSpecName) {
        if (StringUtils.isBlank(agentSpecName)) {
            throw new IllegalArgumentException("AgentSpec name cannot be blank");
        }
        return AGENTSPEC_GROUP_PREFIX
            + NacosAiConfigKeyCodec.encodeManifestGroupNameSegment(agentSpecName);
    }
    
    /**
     * Build the Nacos Config group for a specific AgentSpec version.
     *
     * @param agentSpecName name of AgentSpec
     * @param version       version string, e.g. "v1"
     * @return config group string, e.g. "agentspec__myworker__v1"
     * @throws IllegalArgumentException if agentSpecName or version is blank
     */
    public static String buildAgentSpecVersionGroup(String agentSpecName, String version) {
        if (StringUtils.isBlank(agentSpecName)) {
            throw new IllegalArgumentException("AgentSpec name cannot be blank");
        }
        if (StringUtils.isBlank(version)) {
            throw new IllegalArgumentException("Version cannot be blank");
        }
        return AGENTSPEC_GROUP_PREFIX
            + NacosAiConfigKeyCodec.encodeVersionedGroupSegment(agentSpecName)
            + DOUBLE_UNDERSCORE + NacosAiConfigKeyCodec.encodeVersionedGroupSegment(version);
    }
    
    /**
     * Build AgentSpec resource config info (dataId and group).
     *
     * @param agentSpecName name of AgentSpec
     * @param type resource type (can be null or empty)
     * @param resourceName resource name
     * @return ConfigInfo containing dataId and group
     * @throws IllegalArgumentException if agentSpecName or resourceName is blank
     */
    public static ConfigInfo buildAgentSpecResourceConfigInfo(String agentSpecName, String type,
        String resourceName) {
        if (StringUtils.isBlank(agentSpecName)) {
            throw new IllegalArgumentException("AgentSpec name cannot be blank");
        }
        if (StringUtils.isBlank(resourceName)) {
            throw new IllegalArgumentException("Resource name cannot be blank");
        }
        
        String resourceId = generateResourceId(type, resourceName);
        String dataId = NacosAiConfigKeyCodec.encodeSegment(
            RESOURCE_DATA_ID_PREFIX + resourceId + RESOURCE_DATA_ID_SUFFIX);
        String group = buildAgentSpecGroup(agentSpecName);
        
        return new ConfigInfo(dataId, group);
    }
    
    /**
     * Decode an AgentSpec Nacos Config {@code group} (as stored) into logical name and optional version.
     *
     * @param group physical group, e.g. {@code agentspec__myagent} or {@code agentspec__name__v1}
     * @return array of length 2: {@code [agentSpecName, version]}; {@code version} is {@code null} when not versioned
     */
    public static String[] decodeAgentSpecGroupToNameAndVersion(String group) {
        if (StringUtils.isBlank(group) || !group.startsWith(AGENTSPEC_GROUP_PREFIX)) {
            throw new IllegalArgumentException("Not an AgentSpec config group: " + group);
        }
        String rest = group.substring(AGENTSPEC_GROUP_PREFIX.length());
        int idx = rest.lastIndexOf(DOUBLE_UNDERSCORE);
        if (idx < 0) {
            return new String[] {NacosAiConfigKeyCodec.decodeSegment(rest), null};
        }
        return new String[] {NacosAiConfigKeyCodec.decodeSegment(rest.substring(0, idx)),
            NacosAiConfigKeyCodec.decodeSegment(rest.substring(idx + DOUBLE_UNDERSCORE.length()))};
    }
}
