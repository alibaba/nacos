/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.exception.api;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.utils.StringUtils;

/** Nacos Exception for open API.
 * @author dongyafei
 * @date 2022/7/22
 */
public class NacosApiException extends NacosException {
    
    /**
     * serialVersionUID.
     */
    private static final long serialVersionUID = 2245627968556056573L;
    
    /**
     * http status code.
     */
    private int statusCode;
    
    /**
     * abstract description for error.
     */
    private String errAbstract;
    
    public NacosApiException() {
    }
    
    public NacosApiException(int statusCode, ErrorCode errorCode, Throwable throwable, String message) {
        super(errorCode.getCode(), message, throwable);
        this.statusCode = statusCode;
        this.errAbstract = errorCode.getMsg();
    }
    
    public NacosApiException(int statusCode, ErrorCode errorCode, String message) {
        super(errorCode.getCode(), message);
        this.statusCode = statusCode;
        this.errAbstract = errorCode.getMsg();
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getErrAbstract() {
        if (!StringUtils.isBlank(this.errAbstract)) {
            return this.errAbstract;
        }
        return Constants.NULL;
    }
    
    public void setErrAbstract(String errAbstract) {
        this.errAbstract = errAbstract;
    }
}
