/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.serveridentity;

/**
 * Nacos server identity check result.
 *
 * @author xiweng.yy
 */
public class ServerIdentityResult {
    
    private final ResultStatus status;
    
    private final String message;
    
    private ServerIdentityResult(ResultStatus status, String message) {
        this.status = status;
        this.message = message;
    }
    
    public ResultStatus getStatus() {
        return status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public static ServerIdentityResult success() {
        return new ServerIdentityResult(ResultStatus.MATCHED, "Server identity matched.");
    }
    
    public static ServerIdentityResult noMatched() {
        return new ServerIdentityResult(ResultStatus.NOT_MATCHED, "Server identity not matched.");
    }
    
    public static ServerIdentityResult fail(String message) {
        return new ServerIdentityResult(ResultStatus.FAIL, message);
    }
    
    public enum ResultStatus {
        
        /**
         * Nacos server identity matched.
         */
        MATCHED,
        
        /**
         * Nacos server identity not matched, need authentication.
         */
        NOT_MATCHED,
        
        /**
         * Nacos server identity check failed.
         */
        FAIL;
    }
}
