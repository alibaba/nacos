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

package com.alibaba.nacos.auth.config;

/**
 * Auth relative error codes, start with 5000X.
 *
 * @author xiweng.yy
 */
public enum AuthErrorCode {
    
    /**
     * invalid auth type.
     */
    INVALID_TYPE(50001,
            "Invalid auth type, Please set `nacos.core.auth.system.type`, detail: https://nacos.io/zh-cn/docs/v2/plugin/auth-plugin.html"),
    
    EMPTY_IDENTITY(50002,
            "Empty identity, Please set `nacos.core.auth.server.identity.key` and `nacos.core.auth.server.identity.value`, detail: https://nacos.io/zh-cn/docs/v2/guide/user/auth.html");
    
    private final Integer code;
    
    private final String msg;
    
    public Integer getCode() {
        return code;
    }
    
    public String getMsg() {
        return msg;
    }
    
    AuthErrorCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
