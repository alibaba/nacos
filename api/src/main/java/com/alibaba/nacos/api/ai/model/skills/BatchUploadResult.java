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

package com.alibaba.nacos.api.ai.model.skills;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of batch skill upload from a multi-skill zip archive.
 *
 * @author nacos
 */
public class BatchUploadResult {
    
    private List<String> succeeded;
    
    private List<FailedItem> failed;
    
    public BatchUploadResult() {
        this.succeeded = new ArrayList<>();
        this.failed = new ArrayList<>();
    }
    
    public List<String> getSucceeded() {
        return succeeded;
    }
    
    public void setSucceeded(List<String> succeeded) {
        this.succeeded = succeeded;
    }
    
    public List<FailedItem> getFailed() {
        return failed;
    }
    
    public void setFailed(List<FailedItem> failed) {
        this.failed = failed;
    }
    
    public void addSucceeded(String skillName) {
        this.succeeded.add(skillName);
    }
    
    public void addFailed(String skillName, String reason) {
        this.failed.add(new FailedItem(skillName, reason));
    }
    
    /**
     * Represents a skill that failed during batch upload.
     */
    public static class FailedItem {
        
        private String name;
        
        private String reason;
        
        public FailedItem() {
        }
        
        public FailedItem(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
