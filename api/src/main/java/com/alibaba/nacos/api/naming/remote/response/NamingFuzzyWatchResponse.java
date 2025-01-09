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

package com.alibaba.nacos.api.naming.remote.response;

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;

/**
 * Nacos naming fuzzy watch service response.
 *
 * @author tanyongquan
 */
public class NamingFuzzyWatchResponse extends Response {
    
    public NamingFuzzyWatchResponse() {
    }
    
    public static NamingFuzzyWatchResponse buildSuccessResponse() {
        return new NamingFuzzyWatchResponse();
    }
    
    /**
     * Build fail response.
     *
     * @param message error message
     * @return fail response
     */
    public static NamingFuzzyWatchResponse buildFailResponse(String message) {
        NamingFuzzyWatchResponse result = new NamingFuzzyWatchResponse();
        result.setErrorInfo(ResponseCode.FAIL.getCode(), message);
        return result;
    }
    
}
