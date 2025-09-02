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
import java.util.List;

/**
 * MCP Server Import Validation Result.
 *
 * @author nacos
 */
public class McpServerImportValidationResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Validation success.
     */
    private boolean valid;
    
    /**
     * Parsed servers count.
     */
    private int totalCount;
    
    /**
     * Valid servers count.
     */
    private int validCount;
    
    /**
     * Invalid servers count.
     */
    private int invalidCount;
    
    /**
     * Duplicate servers count.
     */
    private int duplicateCount;
    
    /**
     * Parsed and validated servers.
     */
    private List<McpServerValidationItem> servers;
    
    /**
     * Overall validation errors.
     */
    private List<String> errors;
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
    
    public int getValidCount() {
        return validCount;
    }
    
    public void setValidCount(int validCount) {
        this.validCount = validCount;
    }
    
    public int getInvalidCount() {
        return invalidCount;
    }
    
    public void setInvalidCount(int invalidCount) {
        this.invalidCount = invalidCount;
    }
    
    public int getDuplicateCount() {
        return duplicateCount;
    }
    
    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }
    
    public List<McpServerValidationItem> getServers() {
        return servers;
    }
    
    public void setServers(List<McpServerValidationItem> servers) {
        this.servers = servers;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}