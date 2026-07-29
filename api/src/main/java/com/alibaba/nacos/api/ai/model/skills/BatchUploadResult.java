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

import com.alibaba.nacos.api.model.v2.ErrorCode;

/**
 * Result of one skill in a batch upload.
 *
 * @author nacos
 */
public class BatchUploadResult {
    
    public static final String ERROR_CODE_SUCCESS = "SUCCESS";
    
    public static final String ERROR_CODE_UPLOAD_FAILED = "UPLOAD_FAILED";
    
    private String name;
    
    private boolean success;
    
    private String errorCode;
    
    private String errorMessage;
    
    private String owner;
    
    public BatchUploadResult() {
    }
    
    public BatchUploadResult(String name, boolean success, String errorCode, String errorMessage,
        String owner) {
        this.name = name;
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.owner = owner;
    }
    
    public static BatchUploadResult success(String skillName) {
        return new BatchUploadResult(skillName, true, ERROR_CODE_SUCCESS,
            ErrorCode.SUCCESS.getMsg(), null);
    }
    
    public static BatchUploadResult failure(String skillName, String errorCode,
        String errorMessage) {
        return failure(skillName, errorCode, errorMessage, null);
    }
    
    public static BatchUploadResult failure(String skillName, String errorCode,
        String errorMessage, String owner) {
        return new BatchUploadResult(skillName, false, errorCode, errorMessage, owner);
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
}
