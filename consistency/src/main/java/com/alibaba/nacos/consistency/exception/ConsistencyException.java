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

package com.alibaba.nacos.consistency.exception;

/**
 * Conformance protocol internal exceptions.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConsistencyException extends RuntimeException {
    
    private static final long serialVersionUID = 1935132712388069418L;
    
    public ConsistencyException() {
        super();
    }
    
    public ConsistencyException(String message) {
        super(message);
    }
    
    public ConsistencyException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ConsistencyException(Throwable cause) {
        super(cause);
    }
    
    protected ConsistencyException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
    
}
