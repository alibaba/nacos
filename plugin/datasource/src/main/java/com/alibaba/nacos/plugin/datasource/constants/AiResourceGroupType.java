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

package com.alibaba.nacos.plugin.datasource.constants;

/**
 * AI Resource types stored via AiResourceStorage, whose configs should be hidden from the config list.
 *
 * <p>Each enum value declares its group prefix and optionally known dataId patterns. Two filtering modes:
 * <ul>
 *   <li><b>Group-only</b> ({@code dataIdMatchers == null}): excludes ALL configs matching the group prefix.
 *       Use when dataId formats are too diverse to enumerate.</li>
 *   <li><b>Compound</b> ({@code dataIdMatchers} populated): excludes only configs matching BOTH group prefix
 *       AND one of the dataId patterns. Reduces false positives for user configs sharing group prefixes.</li>
 * </ul>
 *
 * <p>To add a new AI Resource type:
 * <ol>
 *   <li>Add an enum value with its group prefix and dataId matchers (or null for group-only)</li>
 *   <li>Done. SQL filtering will automatically pick it up.</li>
 * </ol>
 *
 * @author sai
 */
public enum AiResourceGroupType {
    
    /**
     * Skill manifest (index) config.
     *
     * <p>Group format: {@code skill_{name}} (no version suffix), e.g. {@code skill_mySkill}.
     * Built by {@code SkillUtils.buildSkillGroup()}, which uses {@code encodeManifestGroupNameSegment}
     * (conditionally encodes to {@code enc.{hex}} only when the name contains invalid characters or {@code __}).
     * DataId is always the fixed value {@code skill_index.json}.</p>
     */
    SKILL_MANIFEST("skill_", new DataIdMatcher[]{
            DataIdMatcher.exact("skill_index.json"),
            DataIdMatcher.exact("skill.json")
    }),
    
    /**
     * Skill version file configs (SKILL.md, resource files, etc.).
     *
     * <p>Group format: {@code skill_enc.{hex}__enc.{hex}}, e.g. {@code skill_enc.6d79...__enc.312e...}.
     * Built by {@code SkillUtils.buildSkillVersionGroup()}, which uses {@code encodeVersionedGroupSegment}
     * (unconditionally encodes both name and version segments), so the group always starts with {@code skill_enc.}.
     * DataIds can be arbitrary file paths ({@code SKILL.md}, {@code README.md}, {@code enc.{hex}}), so group-only
     * filtering is used.</p>
     */
    SKILL_VERSION("skill_enc.", null),
    
    AGENTSPEC("agentspec__", new DataIdMatcher[]{
            DataIdMatcher.like("resource_%"),
            DataIdMatcher.exact("manifest.json"),
            DataIdMatcher.exact("agentspec_index.json")
    }),
    
    PROMPT("prompt__", new DataIdMatcher[]{
            DataIdMatcher.exact("content.json")
    });
    
    private final String groupPrefix;
    
    private final DataIdMatcher[] dataIdMatchers;
    
    AiResourceGroupType(String groupPrefix, DataIdMatcher[] dataIdMatchers) {
        this.groupPrefix = groupPrefix;
        this.dataIdMatchers = dataIdMatchers;
    }
    
    public String getGroupPrefix() {
        return groupPrefix;
    }
    
    /**
     * Get LIKE pattern for group_id SQL: prefix + '%'.
     *
     * @return the LIKE pattern string
     */
    public String getLikePattern() {
        return groupPrefix + "%";
    }
    
    /**
     * Get the dataId matchers for this AI resource type.
     *
     * @return array of DataIdMatcher
     */
    public DataIdMatcher[] getDataIdMatchers() {
        return dataIdMatchers;
    }
    
    /**
     * Check if a given group and dataId match any AI resource pattern.
     *
     * @param group  the group_id value
     * @param dataId the data_id value
     * @return true if the pair matches an internal AI resource config
     */
    public static boolean matches(String group, String dataId) {
        if (group == null) {
            return false;
        }
        for (AiResourceGroupType type : values()) {
            if (!group.startsWith(type.groupPrefix)) {
                continue;
            }
            DataIdMatcher[] matchers = type.dataIdMatchers;
            if (matchers == null) {
                return true;
            }
            if (dataId == null) {
                continue;
            }
            for (DataIdMatcher m : matchers) {
                if (m.like) {
                    String prefix = m.pattern.endsWith("%") ? m.pattern.substring(0, m.pattern.length() - 1) : m.pattern;
                    if (dataId.startsWith(prefix)) {
                        return true;
                    }
                } else {
                    if (dataId.equals(m.pattern)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Describes a dataId matching rule — either a LIKE pattern or an exact value.
     */
    public static class DataIdMatcher {
        
        private final String pattern;
        
        private final boolean like;
        
        private DataIdMatcher(String pattern, boolean like) {
            this.pattern = pattern;
            this.like = like;
        }
        
        /**
         * Create a LIKE matcher (e.g. 'resource_%').
         */
        public static DataIdMatcher like(String pattern) {
            return new DataIdMatcher(pattern, true);
        }
        
        /**
         * Create an exact-match matcher (e.g. 'skill_index.json').
         */
        public static DataIdMatcher exact(String value) {
            return new DataIdMatcher(value, false);
        }
        
        public String getPattern() {
            return pattern;
        }
        
        public boolean isLike() {
            return like;
        }
    }
}
