/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.lock.model;

import java.io.Serializable;

/**
 * Lock operation result returned from server.
 *
 * <p>Replaces the raw Boolean in LockOperationResponse with structured data
 * including reentrant count, wait queue position, and error details.
 *
 * @author DHX
 * @date 2026/05/29
 */
public class LockResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private boolean success;
    
    private int reentrantCount;
    
    private int waitPosition = -1;
    
    private String errorMessage;
    
    public LockResult() {
    }
    
    public LockResult(boolean success) {
        this.success = success;
    }
    
    /**
     * Create success result with reentrant count.
     * @param reentrantCount current reentrant count
     * @return LockResult
     */
    public static LockResult success(int reentrantCount) {
        LockResult result = new LockResult(true);
        result.setReentrantCount(reentrantCount);
        return result;
    }
    
    /**
     * Create fail result with error message.
     * @param errorMessage error description
     * @return LockResult
     */
    public static LockResult fail(String errorMessage) {
        LockResult result = new LockResult(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
    
    /**
     * Create waiting result with queue position.
     * @param waitPosition position in wait queue (0-based, -1 means not queued)
     * @return LockResult
     */
    public static LockResult waiting(int waitPosition) {
        LockResult result = new LockResult(false);
        result.setWaitPosition(waitPosition);
        return result;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isWaiting() {
        return waitPosition >= 0;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
        if (success) {
            this.waitPosition = -1;
            this.errorMessage = null;
        }
    }
    
    public int getReentrantCount() {
        return reentrantCount;
    }
    
    public void setReentrantCount(int reentrantCount) {
        this.reentrantCount = reentrantCount;
    }
    
    public int getWaitPosition() {
        return waitPosition;
    }
    
    public void setWaitPosition(int waitPosition) {
        this.waitPosition = waitPosition;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LockResult that = (LockResult) o;
        return success == that.success
            && reentrantCount == that.reentrantCount
            && waitPosition == that.waitPosition
            && java.util.Objects.equals(errorMessage, that.errorMessage);
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(success, reentrantCount, waitPosition, errorMessage);
    }
    
    @Override
    public String toString() {
        if (success) {
            return "LockResult{success=true, count=" + reentrantCount + "}";
        }
        if (waitPosition >= 0) {
            return "LockResult{success=false, waiting=true, pos=" + waitPosition + "}";
        }
        return "LockResult{success=false, msg=" + errorMessage + "}";
    }
}
