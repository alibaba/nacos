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
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.common.paramcheck.ParamCheckResponse;
import com.alibaba.nacos.common.paramcheck.ParamCheckerManager;
import com.alibaba.nacos.core.paramcheck.ServerParamCheckConfig;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.paramcheck.AbstractParamChecker;
import java.util.Collections;

/** Preflight shape and standard Nacos Skill name checks; no resource lookup. */
public final class HubSkillTransferRequestValidator {
    
    public static final String INITIAL_VERSION = "0.0.1";
    
    private HubSkillTransferRequestValidator() {
    }
    
    /** Validate one transfer mode and resolve its concrete target version. */
    public static String validate(HubSkillTransferRequest request, boolean fromHub)
        throws NacosException {
        if (request == null) {
            throw invalid("Transfer request is required");
        }
        required(request.getTargetNamespaceId(), "targetNamespaceId");
        required(request.getTargetName(), "targetName");
        if (request.getTargetNamespaceId().length() > 128
            || request.getTargetName().length() > 256
            || !request.getTargetNamespaceId().equals(request.getTargetNamespaceId().trim())
            || !request.getTargetName().equals(request.getTargetName().trim())) {
            throw invalid("Target namespace or name is invalid");
        }
        if (HubNamespaceUtils.isHubNamespace(request.getTargetNamespaceId())) {
            throw invalid("Hub namespace cannot be an import target");
        }
        ParamInfo param = new ParamInfo();
        param.setSkillName(request.getTargetName());
        String checkerType = ServerParamCheckConfig.getInstance().getActiveParamChecker();
        AbstractParamChecker checker =
            ParamCheckerManager.getInstance().getParamChecker(checkerType);
        ParamCheckResponse checked = checker
            .checkParamInfoList(Collections.singletonList(param));
        if (!checked.isSuccess()) {
            throw invalid(checked.getMessage());
        }
        if (fromHub) {
            if (request.getHubEntryId() == null || request.getHubEntryId() <= 0
                || request.getSourceNamespaceId() != null
                || request.getSourceName() != null
                || request.getSourceVersion() != null) {
                throw invalid("Hub import requires only hubEntryId as source identity");
            }
        } else {
            if (request.getHubEntryId() != null) {
                throw invalid("Direct copy must not contain hubEntryId");
            }
            required(request.getSourceNamespaceId(), "sourceNamespaceId");
            required(request.getSourceName(), "sourceName");
            required(request.getSourceVersion(), "sourceVersion");
            if (request.getSourceNamespaceId().length() > 128
                || request.getSourceName().length() > 256
                || request.getSourceVersion().length() > 64
                || !request.getSourceNamespaceId().equals(request.getSourceNamespaceId().trim())
                || !request.getSourceName().equals(request.getSourceName().trim())
                || HubNamespaceUtils.isHubNamespace(request.getSourceNamespaceId())
                || "latest".equalsIgnoreCase(request.getSourceVersion())
                || !request.getSourceVersion().equals(request.getSourceVersion().trim())) {
                throw invalid("Direct copy requires a concrete source version outside Hub");
            }
        }
        String version = StringUtils.isBlank(request.getTargetVersion())
            ? INITIAL_VERSION : request.getTargetVersion();
        if (version.length() > 64 || !version.equals(version.trim())
            || !VersionUtils.isSupportedVersionFormat(version)
            || "latest".equalsIgnoreCase(version)) {
            throw invalid("targetVersion must be a concrete version");
        }
        return version;
    }
    
    private static void required(String value, String field) throws NacosException {
        if (StringUtils.isBlank(value)) {
            throw invalid(field + " is required");
        }
    }
    
    private static NacosApiException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
