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

package com.alibaba.nacos.api.lock.remote.response;

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;

/**
 * Client response to server push LockNotificationRequest.
 *
 * @author DHX
 * @date 2026/05/29
 */
public class LockNotificationResponse extends Response {
    
    public LockNotificationResponse() {
    }
    
    /**
     * create success response.
     * @return LockNotificationResponse
     */
    public static LockNotificationResponse success() {
        LockNotificationResponse response = new LockNotificationResponse();
        response.setResultCode(ResponseCode.SUCCESS.getCode());
        return response;
    }
    
    /**
     * create fail response.
     * @param message error message
     * @return LockNotificationResponse
     */
    public static LockNotificationResponse fail(String message) {
        LockNotificationResponse response = new LockNotificationResponse();
        response.setResultCode(ResponseCode.FAIL.getCode());
        response.setMessage(message);
        return response;
    }
}
