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

package com.alibaba.nacos.ai.form.prompt;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;

/**
 * Prompt list query form.
 *
 * @author nacos
 */
public class PromptListForm implements NacosForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private static final int MAX_PAGE_SIZE = 50;
    
    private String namespaceId;
    
    /**
     * Optional promptKey filter.
     */
    private String promptKey;
    
    /**
     * Search mode: "accurate" or "blur".
     */
    private String search;
    
    /**
     * Optional biz tags filter (comma-separated).
     */
    private String bizTags;
    
    /**
     * Page number (1-based).
     */
    private int pageNo = 1;
    
    /**
     * Page size.
     */
    private int pageSize = 10;
    
    @Override
    public void validate() throws NacosApiException {
        fillDefaultNamespaceId();
        
        if (StringUtils.isNotBlank(search)
            && !Constants.Prompt.SEARCH_ACCURATE.equalsIgnoreCase(search)
            && !Constants.Prompt.SEARCH_BLUR.equalsIgnoreCase(search)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Request parameter 'search' should be 'accurate' or 'blur'.");
        }
        
        if (pageNo < 1) {
            pageNo = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
    }
    
    private void fillDefaultNamespaceId() {
        if (StringUtils.isEmpty(namespaceId)) {
            namespaceId = Constants.Prompt.PROMPT_DEFAULT_NAMESPACE;
        }
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getPromptKey() {
        return promptKey;
    }
    
    public void setPromptKey(String promptKey) {
        this.promptKey = promptKey;
    }
    
    public String getSearch() {
        return search;
    }
    
    public void setSearch(String search) {
        this.search = search;
    }
    
    public String getBizTags() {
        return bizTags;
    }
    
    public void setBizTags(String bizTags) {
        this.bizTags = bizTags;
    }
    
    public int getPageNo() {
        return pageNo;
    }
    
    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
