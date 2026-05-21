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

package com.alibaba.nacos.ai.importer.security;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import org.springframework.stereotype.Service;

/**
 * Central guard for import artifacts crossing the plugin boundary.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
@Service
public class AiResourceImportSecurityGuard {
    
    /**
     * Check artifact type and size before validation or import.
     *
     * @param source resolved source
     * @param expectedResourceType expected resource type
     * @param artifact fetched artifact
     * @throws NacosException if the artifact violates the import boundary
     */
    public void checkArtifact(AiResourceImportSource source, String expectedResourceType,
        AiResourceImportArtifact artifact) throws NacosException {
        if (artifact == null) {
            throw invalid("AI resource import artifact must not be null.");
        }
        if (!StringUtils.equals(expectedResourceType, artifact.getResourceType())) {
            throw invalid("AI resource import artifact resource type mismatch.");
        }
        long payloadSize = 0;
        if (artifact.getPayload() != null) {
            payloadSize += artifact.getPayload().length;
        }
        if (artifact.getPayloadJson() != null) {
            payloadSize += artifact.getPayloadJson().length();
        }
        if (source.getMaxArtifactSize() > 0 && payloadSize > source.getMaxArtifactSize()) {
            throw invalid("AI resource import artifact size exceeds source limit.");
        }
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
}
