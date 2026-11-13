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

import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * JSON utility for Nacos AI Resource Hub reference descriptors.
 *
 */
public final class HubResourceReferenceJsonUtil {
    
    private HubResourceReferenceJsonUtil() {
    }
    
    /**
     * Serialize a validated Hub reference descriptor.
     *
     * @param reference Hub reference
     * @return serialized JSON
     * @throws NacosException if the reference is invalid
     */
    public static String toJson(HubResourceReference reference) throws NacosException {
        validate(reference);
        return JacksonUtils.toJson(reference);
    }
    
    /**
     * Parse and validate a Hub reference descriptor.
     *
     * @param json persisted descriptor JSON
     * @return parsed Hub reference
     * @throws NacosException if the descriptor is missing or invalid
     */
    public static HubResourceReference parse(String json) throws NacosException {
        if (StringUtils.isBlank(json)) {
            throw invalidDescriptor("descriptor is missing");
        }
        
        try {
            HubResourceReference reference =
                    JacksonUtils.toObj(json, HubResourceReference.class);
            validate(reference);
            return reference;
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                    "Hub resource reference descriptor is invalid", e);
        }
    }
    
    /**
     * Validate a Hub reference descriptor.
     *
     * @param reference Hub reference
     * @throws NacosException if the descriptor is invalid
     */
    public static void validate(HubResourceReference reference) throws NacosException {
        if (reference == null) {
            throw invalidDescriptor("descriptor is missing");
        }
        
        if (!Integer.valueOf(HubResourceReference.CURRENT_SCHEMA_VERSION)
                .equals(reference.getSchemaVersion())) {
            throw invalidDescriptor("unsupported schemaVersion");
        }
        
        if (!HubResourceReference.ENTRY_KIND.equals(reference.getEntryKind())) {
            throw invalidDescriptor("entryKind must be HUB_REFERENCE");
        }
        
        if (StringUtils.isBlank(reference.getSourceNamespaceId())) {
            throw invalidDescriptor("sourceNamespaceId is missing");
        }
        
        if (reference.getSourceResourceId() == null
                || reference.getSourceResourceId() <= 0) {
            throw invalidDescriptor("sourceResourceId is invalid");
        }
        
        if (StringUtils.isBlank(reference.getSourceName())) {
            throw invalidDescriptor("sourceName is missing");
        }
        
        if (reference.getSourceVersionId() == null
                || reference.getSourceVersionId() <= 0) {
            throw invalidDescriptor("sourceVersionId is invalid");
        }
        
        if (StringUtils.isBlank(reference.getSourceVersion())) {
            throw invalidDescriptor("sourceVersion is missing");
        }
    }
    
    private static NacosException invalidDescriptor(String reason) {
        return new NacosException(NacosException.SERVER_ERROR,
                "Hub resource reference descriptor is invalid: " + reason);
    }
    
    /**
     * Build a Hub reference descriptor.
     *
     * @param sourceNamespaceId source namespace ID
     * @param sourceResourceId source ai_resource ID
     * @param sourceName source resource name
     * @param sourceVersionId source ai_resource_version ID
     * @param sourceVersion source resource version
     * @return Hub reference
     * @throws NacosException if any field is invalid
     */
    public static HubResourceReference build(String sourceNamespaceId,
            Long sourceResourceId, String sourceName,
            Long sourceVersionId, String sourceVersion) throws NacosException {
        
        HubResourceReference reference = new HubResourceReference();
        reference.setSchemaVersion(HubResourceReference.CURRENT_SCHEMA_VERSION);
        reference.setEntryKind(HubResourceReference.ENTRY_KIND);
        reference.setSourceNamespaceId(sourceNamespaceId);
        reference.setSourceResourceId(sourceResourceId);
        reference.setSourceName(sourceName);
        reference.setSourceVersionId(sourceVersionId);
        reference.setSourceVersion(sourceVersion);
        
        validate(reference);
        return reference;
    }
}

