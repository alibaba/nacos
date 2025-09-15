/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.mcp;

import java.io.Serializable;

/**
 * MCP Server Import Request.
 *
 * @author nacos
 */
public class McpServerImportRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Import type: file, url, json.
     */
    private String importType;
    
    /**
     * Import source data.
     */
    private String data;
    
    /**
     * Whether to override existing servers.
     */
    private boolean overrideExisting = false;
    
    /**
     * Whether to validate only (preview mode).
     */
    private boolean validateOnly = false;
    
    /**
     * Whether to skip invalid servers and continue importing valid ones.
     * Default false keeps previous behavior (fail fast when any invalid exists).
     */
    private boolean skipInvalid = false;
    
    /**
     * Selected server IDs for import (for selective import).
     */
    private String[] selectedServers;

    /**
     * Optional start cursor for URL-based pagination.
     * Only effective when importType = url.
     */
    private String cursor;

    /**
    * Optional page size limit for URL import (items per page).
    * Only effective when importType = url. If null, server-side default applies.
     */
    private Integer limit;

    /**
     * Optional fuzzy search keyword for registry listing.
     * Only effective when importType = url. When present, backend will append it
     * to the registry query string as `search` for server-side fuzzy filtering.
     */
    private String search;
    
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

    public boolean isSkipInvalid() {
        return skipInvalid;
    }

    public void setSkipInvalid(boolean skipInvalid) {
        this.skipInvalid = skipInvalid;
    }
}