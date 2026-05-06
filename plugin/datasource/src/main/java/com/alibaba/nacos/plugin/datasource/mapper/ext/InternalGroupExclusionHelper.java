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
import com.alibaba.nacos.plugin.datasource.constants.AiResourceGroupType.DataIdMatcher;

import java.util.List;

/**
 * Utility to build SQL exclusion conditions for internal AI resource configs.
 *
 * <p>Supports two filtering modes per {@link AiResourceGroupType}:
 * <ul>
 *   <li><b>Group-only</b> (dataIdMatchers is null): {@code AND group_id NOT LIKE ?}</li>
 *   <li><b>Compound</b> (dataIdMatchers populated):
 *       {@code AND NOT (group_id LIKE ? AND (data_id LIKE ? OR data_id = ? OR ...))}</li>
 * </ul>
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
     * Append exclusion conditions to a WhereBuilder. Used by interface default methods (standard SQL without ESCAPE).
     *
     * @param where the WhereBuilder to append conditions to
     */
    public static void appendExclusion(WhereBuilder where) {
        for (AiResourceGroupType type : AiResourceGroupType.values()) {
            DataIdMatcher[] matchers = type.getDataIdMatchers();
            if (matchers == null) {
                where.and().notLike("group_id", type.getLikePattern());
            } else {
                where.and().not().startParentheses().like("group_id", type.getLikePattern())
                        .and().startParentheses();
                appendDataIdMatchers(where, matchers, false);
                where.endParentheses().endParentheses();
            }
        }
    }
    
    /**
     * Append exclusion conditions to a raw StringBuilder with parameter list. Used by MySQL/Oracle/Base which use
     * manual StringBuilder SQL building.
     *
     * @param sql       the StringBuilder to append SQL conditions to
     * @param paramList the parameter list to add values to
     */
    public static void appendExclusion(StringBuilder sql, List<Object> paramList) {
        for (AiResourceGroupType type : AiResourceGroupType.values()) {
            DataIdMatcher[] matchers = type.getDataIdMatchers();
            if (matchers == null) {
                sql.append(" AND group_id NOT LIKE ?");
                paramList.add(type.getLikePattern());
            } else {
                sql.append(" AND NOT (group_id LIKE ? AND (");
                paramList.add(type.getLikePattern());
                for (int i = 0; i < matchers.length; i++) {
                    if (i > 0) {
                        sql.append(" OR ");
                    }
                    if (matchers[i].isLike()) {
                        sql.append("data_id LIKE ?");
                    } else {
                        sql.append("data_id = ?");
                    }
                    paramList.add(matchers[i].getPattern());
                }
                sql.append("))");
            }
        }
    }
    
    /**
     * Append exclusion conditions with explicit ESCAPE to a WhereBuilder. Used by Derby which requires explicit ESCAPE
     * clause for LIKE patterns.
     *
     * @param where the WhereBuilder to append conditions to
     */
    public static void appendExclusionWithEscape(WhereBuilder where) {
        for (AiResourceGroupType type : AiResourceGroupType.values()) {
            DataIdMatcher[] matchers = type.getDataIdMatchers();
            if (matchers == null) {
                where.and().notLikeWithEscape("group_id", type.getLikePattern());
            } else {
                where.and().not().startParentheses().likeWithEscape("group_id", type.getLikePattern())
                        .and().startParentheses();
                appendDataIdMatchers(where, matchers, true);
                where.endParentheses().endParentheses();
            }
        }
    }
    
    private static void appendDataIdMatchers(WhereBuilder where, DataIdMatcher[] matchers, boolean withEscape) {
        for (int i = 0; i < matchers.length; i++) {
            if (i > 0) {
                where.or();
            }
            if (matchers[i].isLike()) {
                if (withEscape) {
                    where.likeWithEscape("data_id", matchers[i].getPattern());
                } else {
                    where.like("data_id", matchers[i].getPattern());
                }
            } else {
                where.eq("data_id", matchers[i].getPattern());
            }
        }
    }
}
