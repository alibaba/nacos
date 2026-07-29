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

package com.alibaba.nacos.ai.form.agent.admin;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Query filters for Agent Version summaries.
 *
 * @author Nacos
 */
public class AgentVersionListForm extends AgentAdminForm {
    
    private static final long serialVersionUID = 1L;
    
    private String status;
    
    @Override
    public void validate() throws NacosApiException {
        super.validate();
        if (StringUtils.isNotBlank(status)
            && !AiConstants.Agent.VERSION_STATUS_DRAFT.equals(status)
            && !AiConstants.Agent.VERSION_STATUS_REVIEWING.equals(status)
            && !AiConstants.Agent.VERSION_STATUS_REVIEWED.equals(status)
            && !AiConstants.Agent.VERSION_STATUS_ONLINE.equals(status)
            && !AiConstants.Agent.VERSION_STATUS_OFFLINE.equals(status)) {
            throw new IllegalArgumentException("Invalid Agent Version status: " + status);
        }
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}
