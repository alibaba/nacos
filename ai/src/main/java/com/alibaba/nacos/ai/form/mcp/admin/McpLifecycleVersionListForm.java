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

package com.alibaba.nacos.ai.form.mcp.admin;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Filters for a bounded MCP Version lifecycle page.
 *
 * @author Nacos
 */
public class McpLifecycleVersionListForm extends McpLifecycleForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private static final Set<String> STATUSES = new HashSet<>(Arrays.asList(
        AiResourceConstants.VERSION_STATUS_DRAFT,
        AiResourceConstants.VERSION_STATUS_REVIEWING,
        AiResourceConstants.VERSION_STATUS_REVIEWED,
        AiResourceConstants.VERSION_STATUS_ONLINE,
        AiResourceConstants.VERSION_STATUS_OFFLINE));
    
    private String status;
    
    @Override
    public void validate() throws NacosApiException {
        super.validate();
        if (StringUtils.isNotBlank(status)) {
            String normalizedStatus = status.toLowerCase(Locale.ROOT);
            if (!STATUSES.contains(normalizedStatus)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Invalid MCP Version status: " + status);
            }
            status = normalizedStatus;
        }
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}
