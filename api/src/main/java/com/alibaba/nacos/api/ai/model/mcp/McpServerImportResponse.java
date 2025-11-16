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
 * MCP Server Import Response.
 *
 * @author nacos
 */
public class McpServerImportResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Import success.
     */
    private boolean success;
    
    /**
     * Total count of servers to import.
     */
    private int totalCount = 0;
    
    /**
     * Successfully imported count.
     */
    private int successCount = 0;
    
    /**
     * Failed import count.
     */
    private int failedCount = 0;
    
    /**
     * Skipped count (duplicates).
     */
    private int skippedCount = 0;
    
    /**
     * Import results for each server.
     */
    private List<McpServerImportResult> results;
    
    /**
     * Overall error message.
     */
    private String errorMessage;
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
    
    public int getSuccessCount() {
        return successCount;
    }
    
    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }
    
    public int getFailedCount() {
        return failedCount;
    }
    
    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }
    
    public int getSkippedCount() {
        return skippedCount;
    }
    
    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }
    
    public List<McpServerImportResult> getResults() {
        return results;
    }
    
    public void setResults(List<McpServerImportResult> results) {
        this.results = results;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}