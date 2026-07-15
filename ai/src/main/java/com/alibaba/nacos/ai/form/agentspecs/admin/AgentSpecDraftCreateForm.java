/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.form.agentspecs.admin;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;

/**
 * AgentSpec draft create form.
 *
 * @author nacos
 */
public class AgentSpecDraftCreateForm extends AgentSpecForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String basedOnVersion;
    
    private String targetVersion;
    
    @Override
    public void validate() throws NacosApiException {
        fillDefaultNamespaceId();
        if (StringUtils.isBlank(getAgentSpecName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Request parameter `agentSpecName` should not be blank.");
        }
    }
    
    public String getBasedOnVersion() {
        return basedOnVersion;
    }
    
    public void setBasedOnVersion(String basedOnVersion) {
        this.basedOnVersion = basedOnVersion;
    }
    
    public String getTargetVersion() {
        return targetVersion;
    }
    
    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }
}
