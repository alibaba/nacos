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
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;

import java.io.Serial;

/**
 * Form for updating agentspec visibility scope.
 *
 * @author nacos
 */
public class AgentSpecScopeForm extends AgentSpecForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String scope;
    
    @Override
    public void validate() throws NacosApiException {
        super.validate();
        if (StringUtils.isBlank(scope)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter 'scope' type String is not present");
        }
        if (!VisibilityConstants.SCOPE_PUBLIC.equalsIgnoreCase(scope)
            && !VisibilityConstants.SCOPE_PRIVATE.equalsIgnoreCase(scope)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Parameter 'scope' must be PUBLIC or PRIVATE");
        }
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }
}
