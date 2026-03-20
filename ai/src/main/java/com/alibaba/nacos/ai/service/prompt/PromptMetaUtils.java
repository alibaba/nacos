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
import com.alibaba.nacos.api.ai.model.prompt.PromptDescriptor;
import com.alibaba.nacos.api.ai.model.prompt.PromptLabelVersionMapping;
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
     * Ensure mapping collection fields are initialized.
     *
     * @param mapping prompt label/version mapping
     * @return normalized mapping
     */
    public static PromptLabelVersionMapping normalizeLabelVersionMapping(PromptLabelVersionMapping mapping) {
        if (mapping == null) {
            return null;
        }
        if (mapping.getLabels() == null) {
            mapping.setLabels(new HashMap<>(4));
        }
        if (mapping.getVersions() == null) {
            mapping.setVersions(new ArrayList<>(4));
        }
        return mapping;
    }
    
    /**
     * Ensure descriptor collection fields are initialized.
     *
     * @param descriptor descriptor
     * @return normalized descriptor
     */
    public static PromptDescriptor normalizeDescriptor(PromptDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        if (descriptor.getBizTags() == null) {
            descriptor.setBizTags(new ArrayList<>(4));
        }
        return descriptor;
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
     * Defensive clone for prompt mapping object.
     *
     * @param mapping prompt label/version mapping
     * @return cloned mapping
     */
    public static PromptLabelVersionMapping cloneLabelVersionMapping(PromptLabelVersionMapping mapping) {
        if (mapping == null) {
            return null;
        }
        PromptLabelVersionMapping copied = new PromptLabelVersionMapping();
        copied.setSchemaVersion(mapping.getSchemaVersion());
        copied.setPromptKey(mapping.getPromptKey());
        copied.setLatestVersion(mapping.getLatestVersion());
        copied.setGmtModified(mapping.getGmtModified());
        copied.setVersions(mapping.getVersions() == null ? new ArrayList<>(4) : new ArrayList<>(mapping.getVersions()));
        copied.setLabels(mapping.getLabels() == null ? new HashMap<>(4) : new HashMap<>(mapping.getLabels()));
        return copied;
    }
    
    /**
     * Defensive clone for prompt descriptor object.
     *
     * @param descriptor descriptor
     * @return cloned descriptor
     */
    public static PromptDescriptor cloneDescriptor(PromptDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        PromptDescriptor copied = new PromptDescriptor();
        copied.setSchemaVersion(descriptor.getSchemaVersion());
        copied.setPromptKey(descriptor.getPromptKey());
        copied.setDescription(descriptor.getDescription());
        copied.setGmtModified(descriptor.getGmtModified());
        copied.setBizTags(descriptor.getBizTags() == null ? new ArrayList<>(4) : new ArrayList<>(descriptor.getBizTags()));
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
     * Build empty prompt label/version mapping for first publish flow.
     *
     * @param promptKey prompt key
     * @return initialized prompt mapping
     */
    public static PromptLabelVersionMapping initEmptyLabelVersionMapping(String promptKey) {
        PromptLabelVersionMapping result = new PromptLabelVersionMapping();
        result.setPromptKey(promptKey);
        result.setVersions(new ArrayList<>(4));
        result.setLabels(new HashMap<>(4));
        return result;
    }
    
    /**
     * Build empty prompt descriptor for first publish flow.
     *
     * @param promptKey prompt key
     * @return initialized prompt descriptor
     */
    public static PromptDescriptor initEmptyDescriptor(String promptKey) {
        PromptDescriptor result = new PromptDescriptor();
        result.setPromptKey(promptKey);
        result.setBizTags(new ArrayList<>(4));
        return result;
    }
    
    /**
     * Compose full prompt meta info from mapping and descriptor.
     *
     * @param promptKey prompt key
     * @param mapping runtime mapping
     * @param descriptor descriptor
     * @return merged prompt meta info
     */
    public static PromptMetaInfo composeMetaInfo(String promptKey, PromptLabelVersionMapping mapping, PromptDescriptor descriptor) {
        PromptMetaInfo result = new PromptMetaInfo();
        result.setPromptKey(promptKey);
        if (mapping != null) {
            result.setSchemaVersion(mapping.getSchemaVersion());
            result.setLatestVersion(mapping.getLatestVersion());
            result.setVersions(mapping.getVersions() == null ? new ArrayList<>(4) : new ArrayList<>(mapping.getVersions()));
            result.setLabels(mapping.getLabels() == null ? new HashMap<>(4) : new HashMap<>(mapping.getLabels()));
            result.setGmtModified(mapping.getGmtModified());
        } else {
            result.setVersions(new ArrayList<>(4));
            result.setLabels(new HashMap<>(4));
        }
        if (descriptor != null) {
            result.setSchemaVersion(descriptor.getSchemaVersion());
            result.setDescription(descriptor.getDescription());
            result.setBizTags(descriptor.getBizTags() == null ? new ArrayList<>(4) : new ArrayList<>(descriptor.getBizTags()));
            if (descriptor.getGmtModified() != null) {
                result.setGmtModified(descriptor.getGmtModified());
            }
        } else {
            result.setBizTags(new ArrayList<>(4));
        }
        return normalizeMeta(result);
    }
    
    /**
     * Resolve target version with precedence version > label (default "latest") > latestVersion.
     *
     * @param meta prompt meta
     * @param version requested version
     * @param label requested label
     * @return resolved target version
     * @throws NacosException on invalid parameters or missing resources
     */
    public static String resolveTargetVersion(PromptMetaInfo meta, String version, String label) throws NacosException {
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "prompt latest version not found");
        }
        PromptLabelVersionMapping mapping = new PromptLabelVersionMapping();
        mapping.setLatestVersion(meta.getLatestVersion());
        mapping.setLabels(meta.getLabels());
        mapping.setVersions(meta.getVersions());
        return resolveTargetVersion(mapping, version, label);
    }
    
    /**
     * Resolve target version with precedence version > label (default "latest") > latestVersion.
     *
     * @param mapping prompt label/version mapping
     * @param version requested version
     * @param label requested label
     * @return resolved target version
     * @throws NacosException on invalid parameters or missing resources
     */
    public static String resolveTargetVersion(PromptLabelVersionMapping mapping, String version, String label)
            throws NacosException {
        if (mapping == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "prompt latest version not found");
        }
        if (StringUtils.isNotBlank(version)) {
            if (!PromptVersionUtils.isValidVersion(version)) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "Version must be in format major.minor.patch");
            }
            if (!mapping.getVersions().contains(version)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        String.format("prompt version `%s` not found", version));
            }
            return version;
        }
        if (StringUtils.isNotBlank(label)) {
            String target = mapping.getLabels().get(label);
            if (StringUtils.isBlank(target)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        String.format("prompt label `%s` not found", label));
            }
            return target;
        }
        if (StringUtils.isBlank(mapping.getLatestVersion())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "prompt latest version not found");
        }
        return mapping.getLatestVersion();
    }
}
