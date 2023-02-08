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

package com.alibaba.nacos.api.model.v2;

import java.io.Serializable;

/**
 * Response Result.
 *
 * @author dongyafei
 * @date 2022/7/12
 */
public class Result<T> implements Serializable {
    
    private static final long serialVersionUID = 6258345442767540526L;
    
    private final Integer code;
    
    private final String message;
    
    private final T data;
    
    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    public Result() {
        this(null);
    }
    
    public Result(T data) {
        this(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), data);
    }
    
    public Result(Integer code, String message) {
        this(code, message, null);
    }
    
    /**
     * Success return with nothing.
     * @param <T> data type
     * @return Result
     */
    public static <T> Result<T> success() {
        return new Result<>();
    }
    
    /**
     * Success return with data.
     * @param <T> data type
     * @return Result
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(data);
    }
    
    /**
     * Failed return with message and detail error information.
     * @return Result
     */
    public static Result<String> failure(String message) {
        return new Result<>(ErrorCode.SERVER_ERROR.getCode(), message);
    }
    
    /**
     * Failed return with errorCode and message.
     * @param <T> data type
     * @return Result
     */
    public static <T> Result<T> failure(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMsg());
    }
    
    /**
     * Failed return with errorCode, message and data.
     * @param <T> data type
     * @return Result
     */
    public static <T> Result<T> failure(ErrorCode errorCode, T data) {
        return new Result<>(errorCode.getCode(), errorCode.getMsg(), data);
    }
    
    @Override
    public String toString() {
        return "Result{" + "errorCode=" + code + ", message='" + message + '\'' + ", data=" + data + '}';
    }
    
    public Integer getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public T getData() {
        return data;
    }
}
