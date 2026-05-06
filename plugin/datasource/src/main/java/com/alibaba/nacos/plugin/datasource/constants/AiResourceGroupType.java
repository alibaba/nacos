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

import java.util.ArrayList;
import java.util.List;

/**
 * AI Resource types stored via AiResourceStorage, whose configs should be hidden from the config list.
 *
 * <p>Each enum value declares its group prefix used in config_info.group_id. SQL filtering dynamically iterates all
 * enum values, so no SQL change is needed when adding a new AI Resource type.
 *
 * <p>To add a new AI Resource type:
 * <ol>
 *   <li>Add an enum value with its group prefix</li>
 *   <li>Done. SQL filtering will automatically pick it up.</li>
 * </ol>
 *
 * @author sai
 */
public enum AiResourceGroupType {
    
    SKILL("skill_"),
    
    AGENTSPEC("agentspec__"),
    
    PROMPT("prompt__");
    
    private final String groupPrefix;
    
    AiResourceGroupType(String groupPrefix) {
        this.groupPrefix = groupPrefix;
    }
    
    public String getGroupPrefix() {
        return groupPrefix;
    }
    
    /**
     * Get LIKE pattern for SQL: prefix + '%'.
     *
     * @return the LIKE pattern string
     */
    public String getLikePattern() {
        return groupPrefix + "%";
    }
    
    /**
     * Collect all LIKE patterns from all enum values.
     *
     * @return list of LIKE patterns
     */
    public static List<String> allLikePatterns() {
        List<String> result = new ArrayList<>();
        for (AiResourceGroupType type : values()) {
            result.add(type.getLikePattern());
        }
        return result;
    }
}
