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

package com.alibaba.nacos.common.http.client.response;

import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.IoUtils;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * ApacheClientHttpResponse implementation {@link HttpClientResponse}.
 *
 * @author mai.jh
 */
public class DefaultClientHttpResponse implements HttpClientResponse {
    
    private SimpleHttpResponse response;
    
    private InputStream responseStream;
    
    private Header responseHeader;
    
    public DefaultClientHttpResponse(SimpleHttpResponse response) {
        this.response = response;
    }
    
    @Override
    public int getStatusCode() {
        return this.response.getCode();
    }
    
    @Override
    public String getStatusText() {
        return this.response.getReasonPhrase();
    }
    
    @Override
    public Header getHeaders() {
        if (this.responseHeader == null) {
            this.responseHeader = Header.newInstance();
            org.apache.hc.core5.http.Header[] allHeaders = response.getHeaders();
            for (org.apache.hc.core5.http.Header header : allHeaders) {
                this.responseHeader.addParam(header.getName(), header.getValue());
            }
        }
        return this.responseHeader;
    }
    
    @Override
    public InputStream getBody() {
        byte[] bodyBytes = response.getBody().getBodyBytes();
        if (bodyBytes != null) {
            this.responseStream = new ByteArrayInputStream(bodyBytes);
        } else {
            this.responseStream = new ByteArrayInputStream(new byte[0]);
        }
        return this.responseStream;
    }
    
    @Override
    public void close() {
        IoUtils.closeQuietly(this.responseStream);
    }
}
