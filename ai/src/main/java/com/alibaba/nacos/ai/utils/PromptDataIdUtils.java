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
    
    private PromptDataIdUtils() {
    }
    
    public static String buildMetaDataId(String promptKey) {
        return buildDescriptorDataId(promptKey);
    }
    
    public static String buildDescriptorDataId(String promptKey) {
        return promptKey + Constants.Prompt.DESCRIPTOR_DATA_ID_SUFFIX;
    }
    
    @Deprecated
    public static String buildAdminInfoDataId(String promptKey) {
        return buildDescriptorDataId(promptKey);
    }
    
    public static String buildLabelVersionMappingDataId(String promptKey) {
        return promptKey + Constants.Prompt.LABEL_VERSION_MAPPING_DATA_ID_SUFFIX;
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
        return isDescriptorDataId(dataId);
    }
    
    public static boolean isDescriptorDataId(String dataId) {
        return StringUtils.isNotBlank(dataId)
            && dataId.endsWith(Constants.Prompt.DESCRIPTOR_DATA_ID_SUFFIX);
    }
    
    @Deprecated
    public static boolean isAdminInfoDataId(String dataId) {
        return isDescriptorDataId(dataId);
    }
    
    /**
     * Check whether dataId is prompt label/version mapping dataId.
     *
     * @param dataId config dataId
     * @return true if mapping dataId
     */
    public static boolean isLabelVersionMappingDataId(String dataId) {
        return StringUtils.isNotBlank(dataId)
            && dataId.endsWith(Constants.Prompt.LABEL_VERSION_MAPPING_DATA_ID_SUFFIX);
    }
    
    /**
     * Extract prompt key from prompt meta dataId.
     *
     * @param dataId config dataId
     * @return prompt key if valid, otherwise null
     */
    public static String extractPromptKeyFromMetaDataId(String dataId) {
        return extractPromptKeyFromDescriptorDataId(dataId);
    }
    
    /**
     * Extract prompt key from prompt descriptor dataId.
     *
     * @param dataId config dataId
     * @return prompt key if valid, otherwise null
     */
    public static String extractPromptKeyFromDescriptorDataId(String dataId) {
        if (!isDescriptorDataId(dataId)) {
            return null;
        }
        return dataId.substring(0,
            dataId.length() - Constants.Prompt.DESCRIPTOR_DATA_ID_SUFFIX.length());
    }
    
    @Deprecated
    public static String extractPromptKeyFromAdminInfoDataId(String dataId) {
        return extractPromptKeyFromDescriptorDataId(dataId);
    }
    
    /**
     * Extract prompt key from mapping dataId.
     *
     * @param dataId config dataId
     * @return prompt key if valid, otherwise null
     */
    public static String extractPromptKeyFromLabelVersionMappingDataId(String dataId) {
        if (!isLabelVersionMappingDataId(dataId)) {
            return null;
        }
        return dataId.substring(0,
            dataId.length() - Constants.Prompt.LABEL_VERSION_MAPPING_DATA_ID_SUFFIX.length());
    }
}
