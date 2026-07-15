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

package com.alibaba.nacos.ai.form.skills.admin;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;

/**
 * Skill publish form.
 *
 * @author nacos
 */
public class SkillPublishForm extends SkillForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String version;
    
    /**
     * Legacy latest label update flag.
     *
     * @deprecated since 3.3.0, the latest label is managed by the server. This
     * parameter is ignored and retained only for compatibility with legacy clients.
     */
    @Deprecated(since = "3.3.0")
    private Boolean updateLatestLabel;
    
    @Override
    public void validate() throws NacosApiException {
        fillDefaultNamespaceId();
        if (StringUtils.isBlank(getSkillName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Request parameter `skillName` should not be blank.");
        }
        if (StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Request parameter `version` should not be blank.");
        }
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    /**
     * Get update latest label flag.
     *
     * @return ignored update latest label flag
     * @deprecated since 3.3.0, the latest label is managed by the server. This
     * parameter is ignored and retained only for compatibility with legacy clients.
     */
    @Deprecated(since = "3.3.0")
    public Boolean getUpdateLatestLabel() {
        return updateLatestLabel;
    }
    
    /**
     * Set update latest label flag.
     *
     * @param updateLatestLabel ignored update latest label flag
     * @deprecated since 3.3.0, the latest label is managed by the server. This
     * parameter is ignored and retained only for compatibility with legacy clients.
     */
    @Deprecated(since = "3.3.0")
    public void setUpdateLatestLabel(Boolean updateLatestLabel) {
        this.updateLatestLabel = updateLatestLabel;
    }
}
