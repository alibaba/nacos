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

import java.io.Serializable;

/**
 * Rest result.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RestResult<T> implements Serializable {
    
    private static final long serialVersionUID = 6095433538316185017L;
    
    private int code;
    
    private String message;
    
    private T data;
    
    public RestResult() {
    }
    
    public RestResult(int code, String message, T data) {
        this.code = code;
        this.setMessage(message);
        this.data = data;
    }
    
    public RestResult(int code, T data) {
        this.code = code;
        this.data = data;
    }
    
    public RestResult(int code, String message) {
        this.code = code;
        this.setMessage(message);
    }
    
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public boolean ok() {
        return this.code == 0 || this.code == 200;
    }
    
    @Override
    public String toString() {
        return "RestResult{" + "code=" + code + ", message='" + message + '\'' + ", data=" + data + '}';
    }
    
    public static <T> ResResultBuilder<T> builder() {
        return new ResResultBuilder<T>();
    }
    
    public static final class ResResultBuilder<T> {
        
        private int code;
        
        private String errMsg;
        
        private T data;
        
        private ResResultBuilder() {
        }
        
        public ResResultBuilder<T> withCode(int code) {
            this.code = code;
            return this;
        }
        
        public ResResultBuilder<T> withMsg(String errMsg) {
            this.errMsg = errMsg;
            return this;
        }
        
        public ResResultBuilder<T> withData(T data) {
            this.data = data;
            return this;
        }
    
        /**
         * Build result.
         *
         * @return result
         */
        public RestResult<T> build() {
            RestResult<T> restResult = new RestResult<T>();
            restResult.setCode(code);
            restResult.setMessage(errMsg);
            restResult.setData(data);
            return restResult;
        }
    }
}
