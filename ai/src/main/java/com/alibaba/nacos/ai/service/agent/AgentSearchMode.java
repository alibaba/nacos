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

package com.alibaba.nacos.ai.service.agent;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Locale;

/**
 * Configured RAD Agent Search read mode.
 *
 * @author Nacos
 */
public enum AgentSearchMode {
    
    AUTO,
    
    INDEX,
    
    SCAN;
    
    /**
     * Parse one configured value, defaulting blank input to {@link #AUTO}.
     *
     * @param configured configured value
     * @return parsed mode
     */
    public static AgentSearchMode parse(String configured) {
        String value = StringUtils.isBlank(configured) ? AUTO.name()
            : configured.trim().toUpperCase(Locale.ROOT);
        return AgentSearchMode.valueOf(value);
    }
}
