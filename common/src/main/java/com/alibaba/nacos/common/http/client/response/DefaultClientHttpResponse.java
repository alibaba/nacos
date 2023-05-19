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
import org.apache.http.HttpResponse;
import org.apache.http.client.utils.HttpClientUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * ApacheClientHttpResponse implementation {@link HttpClientResponse}.
 *
 * @author mai.jh
 */
public class DefaultClientHttpResponse implements HttpClientResponse {
    
    private HttpResponse response;
    
    private Header responseHeader;
    
    public DefaultClientHttpResponse(HttpResponse response) {
        this.response = response;
    }
    
    @Override
    public int getStatusCode() {
        return this.response.getStatusLine().getStatusCode();
    }
    
    @Override
    public String getStatusText() {
        return this.response.getStatusLine().getReasonPhrase();
    }
    
    @Override
    public Header getHeaders() {
        if (this.responseHeader == null) {
            this.responseHeader = Header.newInstance();
            org.apache.http.Header[] allHeaders = response.getAllHeaders();
            for (org.apache.http.Header header : allHeaders) {
                this.responseHeader.addParam(header.getName(), header.getValue());
            }
        }
        return this.responseHeader;
    }
    
    @Override
    public InputStream getBody() throws IOException {
        return response.getEntity().getContent();
    }
    
    @Override
    public void close() {
        try {
            if (this.response != null) {
                HttpClientUtils.closeQuietly(response);
            }
        } catch (Exception ex) {
            // ignore
        }
    }
}
