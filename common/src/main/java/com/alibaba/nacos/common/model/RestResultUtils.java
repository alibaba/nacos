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

package com.alibaba.nacos.common.model;

import com.alibaba.nacos.common.model.core.IResultCode;

/**
 * Rest result utils.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RestResultUtils {
    
    public static <T> RestResult<T> success() {
        return RestResult.<T>builder().withCode(200).build();
    }
    
    public static <T> RestResult<T> success(T data) {
        return RestResult.<T>builder().withCode(200).withData(data).build();
    }
    
    public static <T> RestResult<T> success(String msg, T data) {
        return RestResult.<T>builder().withCode(200).withMsg(msg).withData(data).build();
    }
    
    public static <T> RestResult<T> success(int code, T data) {
        return RestResult.<T>builder().withCode(code).withData(data).build();
    }
    
    public static <T> RestResult<T> failed() {
        return RestResult.<T>builder().withCode(500).build();
    }
    
    public static <T> RestResult<T> failed(String errMsg) {
        return RestResult.<T>builder().withCode(500).withMsg(errMsg).build();
    }
    
    public static <T> RestResult<T> failed(int code, T data) {
        return RestResult.<T>builder().withCode(code).withData(data).build();
    }
    
    public static <T> RestResult<T> failed(int code, T data, String errMsg) {
        return RestResult.<T>builder().withCode(code).withData(data).withMsg(errMsg).build();
    }
    
    public static <T> RestResult<T> failedWithMsg(int code, String errMsg) {
        return RestResult.<T>builder().withCode(code).withMsg(errMsg).build();
    }

    public static <T> RestResult<T> buildResult(IResultCode resultCode, T data) {
        return RestResult.<T>builder().withCode(resultCode.getCode()).withMsg(resultCode.getCodeMsg()).withData(data).build();
    }
}
