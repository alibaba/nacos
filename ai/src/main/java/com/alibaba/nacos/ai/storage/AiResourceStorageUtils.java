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

package com.alibaba.nacos.ai.storage;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Utilities for AI resource storage.
 *
 * @author nacos
 */
public final class AiResourceStorageUtils {
    
    private AiResourceStorageUtils() {
    }
    
    /**
     * Resolve the storage provider for new writes. A resource-specific property takes precedence
     * for compatibility, followed by the global AI storage property.
     *
     * @param resourcePropertyKey resource-specific compatibility property
     * @param defaultProvider default provider
     * @return resolved provider
     */
    public static String resolveProvider(String resourcePropertyKey, String defaultProvider) {
        String provider = EnvUtil.getProperty(resourcePropertyKey);
        if (StringUtils.isBlank(provider)) {
            provider = EnvUtil.getProperty(Constants.AI_STORAGE_PROVIDER_CONFIG_KEY,
                defaultProvider);
        }
        return StringUtils.isBlank(provider) ? defaultProvider : provider.trim();
    }
}
