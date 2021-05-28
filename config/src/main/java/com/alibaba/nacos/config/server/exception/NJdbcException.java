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

package com.alibaba.nacos.config.server.exception;

import org.springframework.dao.DataAccessException;

/**
 * NJdbcException.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NJdbcException extends DataAccessException {
    
    private String originExceptionName;
    
    public NJdbcException(String msg) {
        super(msg);
    }
    
    public NJdbcException(String msg, String originExceptionName) {
        super(msg);
        this.originExceptionName = originExceptionName;
    }
    
    public NJdbcException(String msg, Throwable cause, String originExceptionName) {
        super(msg, cause);
        this.originExceptionName = originExceptionName;
    }
    
    public NJdbcException(String msg, Throwable cause) {
        super(msg, cause);
    }
    
    public NJdbcException(Throwable cause) {
        super("", cause);
    }
}
