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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Prompt data id utility methods.
 *
 * @author nacos
 */
public final class PromptDataIdUtils {
    
    private static final String META_SUFFIX = ".meta" + Constants.Prompt.PROMPT_DATA_ID_SUFFIX;
    
    private PromptDataIdUtils() {
    }
    
    public static String buildMetaDataId(String promptKey) {
        return promptKey + META_SUFFIX;
    }
    
    public static String buildLatestDataId(String promptKey) {
        return promptKey + Constants.Prompt.PROMPT_DATA_ID_SUFFIX;
    }
    
    public static String buildVersionDataId(String promptKey, String version) {
        return promptKey + "." + version + Constants.Prompt.PROMPT_DATA_ID_SUFFIX;
    }
    
    /**
     * Check whether dataId is prompt meta dataId.
     *
     * @param dataId config dataId
     * @return true if meta dataId
     */
    public static boolean isMetaDataId(String dataId) {
        return StringUtils.isNotBlank(dataId) && dataId.endsWith(META_SUFFIX);
    }
    
    /**
     * Extract prompt key from prompt meta dataId.
     *
     * @param dataId config dataId
     * @return prompt key if valid, otherwise null
     */
    public static String extractPromptKeyFromMetaDataId(String dataId) {
        if (!isMetaDataId(dataId)) {
            return null;
        }
        return dataId.substring(0, dataId.length() - META_SUFFIX.length());
    }
}
