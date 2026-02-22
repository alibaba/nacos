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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.ai.utils.PromptVersionUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Prompt meta utility methods.
 *
 * @author nacos
 */
public final class PromptMetaUtils {
    
    private PromptMetaUtils() {
    }
    
    /**
     * Ensure mutable collection fields are initialized.
     *
     * @param meta prompt meta
     * @return normalized prompt meta
     */
    public static PromptMetaInfo normalizeMeta(PromptMetaInfo meta) {
        if (meta == null) {
            return null;
        }
        if (meta.getLabels() == null) {
            meta.setLabels(new HashMap<>(4));
        }
        if (meta.getVersions() == null) {
            meta.setVersions(new ArrayList<>(4));
        }
        if (meta.getBizTags() == null) {
            meta.setBizTags(new ArrayList<>(4));
        }
        return meta;
    }
    
    /**
     * Defensive clone for prompt meta object.
     *
     * @param meta prompt meta
     * @return cloned prompt meta
     */
    public static PromptMetaInfo cloneMeta(PromptMetaInfo meta) {
        if (meta == null) {
            return null;
        }
        PromptMetaInfo copied = new PromptMetaInfo();
        copied.setSchemaVersion(meta.getSchemaVersion());
        copied.setPromptKey(meta.getPromptKey());
        copied.setDescription(meta.getDescription());
        copied.setLatestVersion(meta.getLatestVersion());
        copied.setGmtModified(meta.getGmtModified());
        copied.setBizTags(meta.getBizTags() == null ? new ArrayList<>(4) : new ArrayList<>(meta.getBizTags()));
        copied.setVersions(meta.getVersions() == null ? new ArrayList<>(4) : new ArrayList<>(meta.getVersions()));
        copied.setLabels(meta.getLabels() == null ? new HashMap<>(4) : new HashMap<>(meta.getLabels()));
        return copied;
    }
    
    /**
     * Build empty prompt meta for first publish flow.
     *
     * @param promptKey prompt key
     * @return initialized prompt meta
     */
    public static PromptMetaInfo initEmptyMeta(String promptKey) {
        PromptMetaInfo result = new PromptMetaInfo();
        result.setPromptKey(promptKey);
        result.setVersions(new ArrayList<>(4));
        result.setLabels(new HashMap<>(4));
        result.setBizTags(new ArrayList<>(4));
        return result;
    }
    
    /**
     * Resolve target version with precedence label > version > latest.
     *
     * @param meta prompt meta
     * @param version requested version
     * @param label requested label
     * @return resolved target version
     * @throws NacosException on invalid parameters or missing resources
     */
    public static String resolveTargetVersion(PromptMetaInfo meta, String version, String label) throws NacosException {
        if (StringUtils.isNotBlank(label)) {
            String target = meta.getLabels().get(label);
            if (StringUtils.isBlank(target)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        String.format("prompt label `%s` not found", label));
            }
            return target;
        }
        if (StringUtils.isNotBlank(version)) {
            if (!PromptVersionUtils.isValidVersion(version)) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "Version must be in format major.minor.patch");
            }
            if (!meta.getVersions().contains(version)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        String.format("prompt version `%s` not found", version));
            }
            return version;
        }
        if (StringUtils.isBlank(meta.getLatestVersion())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "prompt latest version not found");
        }
        return meta.getLatestVersion();
    }
}
