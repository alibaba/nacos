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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Utility for generating internal resource names in Nacos AI Resource Hub.
 *
 * @author xyp
 */
public final class HubResourceNameUtils {
    
    /** Database column bound for both Hub resource and version names. */
    static final int MAX_RESOURCE_NAME_LENGTH = 256;
    
    private HubResourceNameUtils() {
    }
    
    /**
     * Build a stable name by retaining the full immutable source ID suffix.
     *
     * <p>The database column limit must be safe for UTF-16-based stores such as
     * Derby and for character-count-based stores. Therefore this method uses
     * Java UTF-16 units as the conservative upper bound and avoids splitting a
     * surrogate pair. A code-point-only limit can exceed Derby VARCHAR(256).</p>
     *
     * @param sourceName source resource name
     * @param sourceResourceId immutable source meta primary key
     * @return Hub internal name that retains the full source ID
     */
    public static String buildInternalName(String sourceName, Long sourceResourceId) {
        if (StringUtils.isBlank(sourceName)) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        if (sourceResourceId == null || sourceResourceId <= 0) {
            throw new IllegalArgumentException("sourceResourceId must be positive");
        }
        String suffix = "-" + sourceResourceId;
        int capacity = MAX_RESOURCE_NAME_LENGTH - suffix.length();
        if (capacity <= 0) {
            throw new IllegalArgumentException("sourceResourceId is too long");
        }
        if (sourceName.length() <= capacity) {
            return sourceName + suffix;
        }
        int end = capacity;
        if (end < sourceName.length() && Character.isHighSurrogate(sourceName.charAt(end - 1))
            && Character.isLowSurrogate(sourceName.charAt(end))) {
            end--;
        }
        return sourceName.substring(0, end) + suffix;
    }
}
