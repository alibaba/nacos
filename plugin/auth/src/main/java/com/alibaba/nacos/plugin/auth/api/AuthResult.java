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

package com.alibaba.nacos.plugin.auth.api;

/**
 * Auth result for auth plugin.
 *
 * @author xiweng.yy
 */
public class AuthResult<T> {
    
    private static final String MESSAGE_FORMAT = "Code: %d, Message: %s.";
    
    private boolean success;
    
    private int errorCode;
    
    private String errorMessage;
    
    /**
     * Optional, If some auth data need to return.
     */
    private T data;
    
    /**
     * Build success result.
     *
     * @return success result
     */
    public static AuthResult successResult() {
        AuthResult result = new AuthResult();
        result.setSuccess(true);
        return result;
    }
    
    /**
     * Build success result.
     *
     * @return success result
     */
    public static AuthResult successResult(Object data) {
        AuthResult result = new AuthResult();
        result.setSuccess(true);
        result.setData(data);
        return result;
    }
    
    /**
     * Build failed result.
     *
     * @return failed result
     */
    public static AuthResult failureResult(int errorCode, String errorMessage) {
        AuthResult result = new AuthResult();
        result.setSuccess(false);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        return result;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public int getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public String format() {
        return String.format(MESSAGE_FORMAT, errorCode, errorMessage);
    }
}
