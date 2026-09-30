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
 *
 */

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Utility for generating internal resource names in Nacos AI Resource Hub.
 *
 * @author xyp
 */
public final class HubResourceNameUtils {
    
    /**
     * Maximum length of ai_resource.name and ai_resource_version.name.
     */
    static final int MAX_RESOURCE_NAME_LENGTH = 256;
    
    private HubResourceNameUtils() {
    }
    
    /**
     * Builds the persistent internal name of a Hub resource.
     *
     * <p>The internal name is composed of the original source resource name and
     * the immutable source resource ID, for example {@code find-skills-101}.
     * If the result exceeds the database name limit, only the source name part
     * is truncated and the complete {@code -<sourceResourceId>} suffix is retained.</p>
     *
     * @param sourceName source resource name
     * @param sourceResourceId source ai_resource ID
     * @return Hub internal resource name
     */
    public static String buildInternalName(String sourceName, Long sourceResourceId) {
        if (StringUtils.isBlank(sourceName)) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        if (sourceResourceId == null || sourceResourceId <= 0) {
            throw new IllegalArgumentException("sourceResourceId must be positive");
        }
        
        String suffix = "-" + sourceResourceId;
        int suffixLength = suffix.codePointCount(0, suffix.length());
        int maxSourceNameLength = MAX_RESOURCE_NAME_LENGTH - suffixLength;
        
        if (maxSourceNameLength <= 0) {
            throw new IllegalArgumentException("sourceResourceId is too long");
        }
        
        int sourceNameLength = sourceName.codePointCount(0, sourceName.length());
        if (sourceNameLength <= maxSourceNameLength) {
            return sourceName + suffix;
        }
        
        int endIndex = sourceName.offsetByCodePoints(0, maxSourceNameLength);
        return sourceName.substring(0, endIndex) + suffix;
    }
}
