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

package com.alibaba.nacos.plugin.datasource.mapper.ext;

import com.alibaba.nacos.plugin.datasource.constants.AiResourceGroupType;

import java.util.List;

/**
 * Utility to build SQL exclusion conditions for internal AI resource configs.
 *
 * <p>Reads patterns from {@link AiResourceGroupType} enum dynamically. When a new AI Resource type is added to the
 * enum, the exclusion SQL will automatically include it without any code change here.
 *
 * @author sai
 */
public final class InternalGroupExclusionHelper {
    
    private InternalGroupExclusionHelper() {
    }
    
    /**
     * Append NOT LIKE conditions to a WhereBuilder. Used by interface default methods (standard SQL without ESCAPE).
     *
     * @param where the WhereBuilder to append conditions to
     */
    public static void appendExclusion(WhereBuilder where) {
        for (AiResourceGroupType type : AiResourceGroupType.values()) {
            where.and().notLike("group_id", type.getLikePattern());
        }
    }
    
    /**
     * Append NOT LIKE conditions to a raw StringBuilder with parameter list. Used by MySQL/Oracle/Base which use manual
     * StringBuilder SQL building.
     *
     * @param sql       the StringBuilder to append SQL conditions to
     * @param paramList the parameter list to add LIKE pattern values to
     */
    public static void appendExclusion(StringBuilder sql, List<Object> paramList) {
        for (AiResourceGroupType type : AiResourceGroupType.values()) {
            sql.append(" AND group_id NOT LIKE ?");
            paramList.add(type.getLikePattern());
        }
    }
    
    /**
     * Append NOT LIKE conditions with explicit ESCAPE to a WhereBuilder. Used by Derby which requires explicit ESCAPE
     * clause.
     *
     * @param where the WhereBuilder to append conditions to
     */
    public static void appendExclusionWithEscape(WhereBuilder where) {
        for (AiResourceGroupType type : AiResourceGroupType.values()) {
            where.and().notLikeWithEscape("group_id", type.getLikePattern());
        }
    }
}
