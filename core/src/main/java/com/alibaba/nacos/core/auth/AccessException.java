/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.api.exception.NacosException;

/**
 * Exception to be thrown if authorization is failed.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class AccessException extends NacosException {

    public AccessException(){

    }

    public AccessException(int code) {
        this.setErrCode(code);
    }

    public AccessException(int code, String msg) {
        this.setErrCode(code);
        this.setErrMsg(msg);
    }

    public static final int CODE_USER_NOT_FOUND = 101001;
    public static final int CODE_PASSWORD_INCORRECT = 101002;
    public static final int CODE_TOKEN_INVALID = 101003;
    public static final int CODE_TOKEN_EXPIRED = 101004;
    public static final int CODE_RESOURCE_INVALID = 101005;
    public static final int CODE_AUTHORIZATION_FAILED = 101006;
    public static final int CODE_ROLE_NOT_FOUND = 101007;

}
