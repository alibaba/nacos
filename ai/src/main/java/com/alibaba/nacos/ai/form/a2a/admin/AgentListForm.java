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
 *
 */

package com.alibaba.nacos.ai.form.a2a.admin;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;

import java.io.Serial;

/**
 * Agent list form.
 *
 * @author KiteSoar
 */
public class AgentListForm extends AgentForm {
    
    @Serial
    private static final long serialVersionUID = 4706219418699928980L;
    
    private String search;
    
    @Override
    public void validate() throws NacosApiException {
        fillDefaultNamespaceId();
        if (!Constants.MCP_LIST_SEARCH_ACCURATE.equalsIgnoreCase(search)
                && !Constants.MCP_LIST_SEARCH_BLUR.equalsIgnoreCase(search)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Request parameter `search` should be `accurate` or `blur`.");
        }
    }
    
    public String getSearch() {
        return search;
    }
    
    public void setSearch(String search) {
        this.search = search;
    }
}
