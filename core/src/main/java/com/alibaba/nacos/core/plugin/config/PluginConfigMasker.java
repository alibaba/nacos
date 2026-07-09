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

package com.alibaba.nacos.core.plugin.config;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Plugin configuration value masker.
 *
 * @author Nacos
 */
public final class PluginConfigMasker {
    
    private static final String MASKED_VALUE = "******";
    
    private static final int TINY_VALUE_MAX_LENGTH = 2;
    
    private static final int SHORT_VALUE_MAX_LENGTH = 8;
    
    private static final int SHORT_VALUE_RETAIN_LENGTH = 1;
    
    private static final int LONG_VALUE_RETAIN_LENGTH = 2;
    
    private PluginConfigMasker() {
    }
    
    /**
     * Mask sensitive value when needed.
     *
     * @param definition config item definition
     * @param value original value
     * @return masked value or original value
     */
    public static String mask(ConfigItemDefinition definition, String value) {
        if (definition.isSensitive() && StringUtils.isNotEmpty(value)) {
            return maskValue(value);
        }
        return value;
    }
    
    private static String maskValue(String value) {
        int length = value.length();
        if (length <= TINY_VALUE_MAX_LENGTH) {
            return MASKED_VALUE;
        }
        if (length <= SHORT_VALUE_MAX_LENGTH) {
            return value.substring(0, SHORT_VALUE_RETAIN_LENGTH) + MASKED_VALUE
                + value.substring(length - SHORT_VALUE_RETAIN_LENGTH);
        }
        return value.substring(0, LONG_VALUE_RETAIN_LENGTH) + MASKED_VALUE
            + value.substring(length - LONG_VALUE_RETAIN_LENGTH);
    }
}
