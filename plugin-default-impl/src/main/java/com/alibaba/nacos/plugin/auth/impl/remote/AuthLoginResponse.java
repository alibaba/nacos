/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.remote;

import com.alibaba.nacos.api.remote.response.Response;

/**
 * Response of auth login.
 *
 * @author Nacos
 */
public class AuthLoginResponse extends Response {
    
    private String accessToken;
    
    private Long tokenTtl;
    
    public AuthLoginResponse() {
    
    }
    
    public AuthLoginResponse(String accessToken, Long tokenTtl) {
        this.accessToken = accessToken;
        this.tokenTtl = tokenTtl;
    }
    
    /**
     * Denied a Request.
     *
     * @param errorMsg errorMsg
     * @return AuthLoginResponse
     */
    public static AuthLoginResponse denied(String errorMsg) {
        AuthLoginResponse loginResponse = new AuthLoginResponse();
        loginResponse.setErrorInfo(403, errorMsg);
        return loginResponse;
    }
    
    /**
     * Getter method for property <tt>accessToken</tt>.
     *
     * @return property value of accessToken
     */
    public String getAccessToken() {
        return accessToken;
    }
    
    /**
     * Setter method for property <tt>accessToken</tt>.
     *
     * @param accessToken value to be assigned to property accessToken
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    /**
     * Getter method for property <tt>tokenTtl</tt>.
     *
     * @return property value of tokenTtl
     */
    public Long getTokenTtl() {
        return tokenTtl;
    }
    
    /**
     * Setter method for property <tt>tokenTtl</tt>.
     *
     * @param tokenTtl value to be assigned to property tokenTtl
     */
    public void setTokenTtl(Long tokenTtl) {
        this.tokenTtl = tokenTtl;
    }
    
}
