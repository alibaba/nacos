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

package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.model.RestResult;

/**
 * Http RestResult.
 *
 * @author mai.jh
 */
public class HttpRestResult<T> extends RestResult<T> {
    
    private static final long serialVersionUID = 3766947816720175947L;
    
    private Header header;
    
    public HttpRestResult() {
    }
    
    public HttpRestResult(Header header, int code, T data, String message) {
        super(code, message, data);
        this.header = header;
    }
    
    public Header getHeader() {
        return header;
    }
    
    public void setHeader(Header header) {
        this.header = header;
    }
}
