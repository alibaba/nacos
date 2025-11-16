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

package com.alibaba.nacos.ai.form.mcp.admin;

import com.alibaba.nacos.ai.enums.ExternalDataTypeEnum;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;

/**
 * Nacos AI MCP Server import request form.
 *
 * @author WangzJi
 */
public class McpImportForm extends McpForm {
    
    @Serial
    private static final long serialVersionUID = 8016131725604983671L;
    
    private String importType;
    
    private String data;
    
    private boolean overrideExisting = false;
    
    private boolean validateOnly = false;
    
    /**
     * Whether to skip invalid servers when executing import.
     */
    private boolean skipInvalid = false;
    
    private String[] selectedServers;
    
    /**
     * Optional start cursor for URL-based import pagination.
     */
    private String cursor;
    
    /**
     * Optional page size for URL-based import (items per page).
     */
    private Integer limit;
    
    /**
     * Optional fuzzy search keyword for registry import listing.
     * Only used when importType is 'url'.
     */
    private String search;
    
    @Override
    public void validate() throws NacosApiException {
        fillDefaultValue();
        if (StringUtils.isEmpty(importType)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'importType' is not present");
        }
        if (StringUtils.isEmpty(data)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'data' is not present");
        }
        if (ExternalDataTypeEnum.parseType(importType) == null) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "importType must be one of: json, url, file");
        }
    }
    
    public String getImportType() {
        return importType;
    }
    
    public void setImportType(String importType) {
        this.importType = importType;
    }
    
    public String getData() {
        return data;
    }
    
    public void setData(String data) {
        this.data = data;
    }
    
    public boolean isOverrideExisting() {
        return overrideExisting;
    }
    
    public void setOverrideExisting(boolean overrideExisting) {
        this.overrideExisting = overrideExisting;
    }
    
    public boolean isValidateOnly() {
        return validateOnly;
    }
    
    public void setValidateOnly(boolean validateOnly) {
        this.validateOnly = validateOnly;
    }
    
    public boolean isSkipInvalid() {
        return skipInvalid;
    }
    
    public void setSkipInvalid(boolean skipInvalid) {
        this.skipInvalid = skipInvalid;
    }
    
    public String[] getSelectedServers() {
        return selectedServers;
    }
    
    public void setSelectedServers(String[] selectedServers) {
        this.selectedServers = selectedServers;
    }
    
    public String getCursor() {
        return cursor;
    }
    
    public void setCursor(String cursor) {
        this.cursor = cursor;
    }
    
    public Integer getLimit() {
        return limit;
    }
    
    public void setLimit(Integer limit) {
        this.limit = limit;
    }
    
    public String getSearch() {
        return search;
    }
    
    public void setSearch(String search) {
        this.search = search;
    }
}
