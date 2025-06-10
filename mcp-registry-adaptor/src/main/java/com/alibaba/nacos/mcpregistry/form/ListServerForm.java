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

package com.alibaba.nacos.mcpregistry.form;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.http.HttpStatus;

/**
 * List mcp server form.
 * @author xinluo
 */
public class ListServerForm implements NacosForm {
    
    private String namespaceId;
     
    private int offset = 0;
    
    private int limit = 30;
    
    private String serverName;
    
    private String searchMode;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getSearchMode() {
        return searchMode;
    }

    public void setSearchMode(String searchMode) {
        this.searchMode = searchMode;
    }

    /**
     * check form parameters while valid.
     *
     * @throws NacosApiException when form parameters is invalid.
     */
    @Override
    public void validate() throws NacosApiException {
        if (offset < 0) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Parameter 'offset' must >= 0");
        }
        
        if (limit > Constants.MAX_LIST_SIZE) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Parameter 'limit' must <= 100");
        }
        
        if (StringUtils.isNotEmpty(searchMode)) {
            if (!Constants.MCP_LIST_SEARCH_BLUR.equals(searchMode) && !Constants.MCP_LIST_SEARCH_ACCURATE.equals(searchMode)) {
                throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISMATCH,
                        "Parameter 'searchMode' must be " + Constants.MCP_LIST_SEARCH_BLUR + " or " + Constants.MCP_LIST_SEARCH_ACCURATE);
            }
        }
        
    }
}
