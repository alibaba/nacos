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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Pure request-shape validation, performed before authorization and persistence.
 *
 * <p>Source qualification (PUBLIC, enable, online).</p>
 *
 * @author nacos
 */
public final class HubSkillRequestValidator {
    
    private static final int MAX_TAKEDOWN_REASON_LENGTH = 512;
    
    private HubSkillRequestValidator() {
    }
    
    /**
     * Validate the source identity and explicit version of a share request.
     *
     * @param request request to validate
     * @throws NacosApiException if the request shape is invalid
     */
    public static void validate(HubShareSkillRequest request) throws NacosApiException {
        if (request == null) {
            throw invalid("share request is missing");
        }
        required(request.getSourceNamespaceId(), "sourceNamespaceId");
        if (HubNamespaceUtils.isHubNamespace(request.getSourceNamespaceId())) {
            throw invalid("Hub namespace cannot be used as a source namespace");
        }
        required(request.getSourceName(), "sourceName");
        exactVersion(request.getSourceVersion(), "sourceVersion");
        if (request.getExpectedSourceResourceId() != null) {
            positive(request.getExpectedSourceResourceId(), "expectedSourceResourceId");
        }
    }
    
    /**
     * Validate the Hub entry, expected meta version and target version.
     *
     * @param request request to validate
     * @throws NacosApiException if the request shape is invalid
     */
    public static void validate(HubAdjustShareRequest request) throws NacosApiException {
        if (request == null) {
            throw invalid("adjust request is missing");
        }
        positive(request.getHubEntryId(), "hubEntryId");
        positive(request.getExpectedMetaVersion(), "expectedMetaVersion");
        exactVersion(request.getTargetVersion(), "targetVersion");
    }
    
    /**
     * Validate the Hub entry and expected meta version of an unshare request.
     *
     * @param request request to validate
     * @throws NacosApiException if the request shape is invalid
     */
    public static void validate(HubUnshareRequest request) throws NacosApiException {
        if (request == null) {
            throw invalid("unshare request is missing");
        }
        positive(request.getHubEntryId(), "hubEntryId");
        positive(request.getExpectedMetaVersion(), "expectedMetaVersion");
    }
    
    /**
     * Validate the Hub entry, expected meta version and administrative reason.
     *
     * @param request request to validate
     * @throws NacosApiException if the request shape is invalid
     */
    public static void validate(HubForceUnshareRequest request) throws NacosApiException {
        if (request == null) {
            throw invalid("administrative unshare request is missing");
        }
        positive(request.getHubEntryId(), "hubEntryId");
        positive(request.getExpectedMetaVersion(), "expectedMetaVersion");
        required(request.getReason(), "reason");
        if (request.getReason().length() > MAX_TAKEDOWN_REASON_LENGTH) {
            throw invalid("reason is too long");
        }
    }
    
    private static void exactVersion(String version, String field) throws NacosApiException {
        required(version, field);
        if ("latest".equalsIgnoreCase(version.trim())) {
            throw invalid(field + " must be an explicit version, not the latest label");
        }
    }
    
    private static void required(String value, String field) throws NacosApiException {
        if (StringUtils.isBlank(value)) {
            throw invalid(field + " is required");
        }
    }
    
    private static void positive(Long value, String field) throws NacosApiException {
        if (value == null || value <= 0) {
            throw invalid(field + " must be positive");
        }
    }
    
    private static NacosApiException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
