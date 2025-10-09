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

package com.alibaba.nacos.ai.form.a2a.admin;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;

import static com.alibaba.nacos.api.ai.constant.AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
import static com.alibaba.nacos.api.ai.constant.AiConstants.A2a.A2A_ENDPOINT_TYPE_URL;

/**
 * Agent Card Form request.
 *
 * @author xiweng.yy
 */
public class AgentCardForm extends AgentForm {
    
    @Serial
    private static final long serialVersionUID = 8361628138801381818L;
    
    private String agentCard;
    
    @Override
    public void validate() throws NacosApiException {
        fillDefaultNamespaceId();
        fillDefaultRegistrationType();
        if (StringUtils.isEmpty(agentCard)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Request parameter `agentCard` should not be `null` or empty.");
        }
        validateRegistrationType();
        
    }
    
    protected void validateRegistrationType() throws NacosApiException {
        if (!A2A_ENDPOINT_TYPE_URL.equals(getRegistrationType()) && !A2A_ENDPOINT_TYPE_SERVICE.equals(
                getRegistrationType())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    String.format("Required parameter 'registrationType' value should be `%s` or `%s` but was `%s`",
                            A2A_ENDPOINT_TYPE_URL, A2A_ENDPOINT_TYPE_SERVICE, getRegistrationType()));
        }
    }
    
    protected void fillDefaultRegistrationType() {
        if (StringUtils.isEmpty(getRegistrationType())) {
            setRegistrationType(A2A_ENDPOINT_TYPE_URL);
        }
    }
    
    public String getAgentCard() {
        return agentCard;
    }
    
    public void setAgentCard(String agentCard) {
        this.agentCard = agentCard;
    }
}
