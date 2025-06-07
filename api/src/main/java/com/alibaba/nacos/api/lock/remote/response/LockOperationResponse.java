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

package com.alibaba.nacos.api.lock.remote.response;

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;

/**
 * grpc acquire lock response.
 *
 * @author 985492783@qq.com
 * @description AcquireLockResponse
 * @date 2023/6/29 13:51
 */
public class LockOperationResponse extends Response {
    
    private Object result;
    
    public LockOperationResponse() {
    
    }
    
    public LockOperationResponse(Boolean result) {
        this.result = result;
    }
    
    /**
     * create success response.
     * @param result result
     * @return LockOperationResponse
     */
    public static LockOperationResponse success(Boolean result) {
        LockOperationResponse response = new LockOperationResponse(result);
        return response;
    }
    
    /**
     * create fail response.
     * @param message message
     * @return LockOperationResponse
     */
    public static LockOperationResponse fail(String message) {
        LockOperationResponse response = new LockOperationResponse(false);
        response.setResultCode(ResponseCode.FAIL.getCode());
        response.setMessage(message);
        return response;
    }
    
    public Object getResult() {
        return result;
    }
    
    public void setResult(Object result) {
        this.result = result;
    }
}
