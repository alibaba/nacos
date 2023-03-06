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

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.IoUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

/**
 * JDk http client response implement.
 *
 * @author mai.jh
 */
public class JdkHttpClientResponse implements HttpClientResponse {
    
    private final HttpURLConnection conn;
    
    private InputStream responseStream;
    
    private Header responseHeader;
    
    private static final String CONTENT_ENCODING = "gzip";
    
    public JdkHttpClientResponse(HttpURLConnection conn) {
        this.conn = conn;
    }
    
    @Override
    public Header getHeaders() {
        if (this.responseHeader == null) {
            this.responseHeader = Header.newInstance();
        }
        for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
            this.responseHeader.addOriginalResponseHeader(entry.getKey(), entry.getValue());
        }
        return this.responseHeader;
    }
    
    @Override
    public InputStream getBody() throws IOException {
        Header headers = getHeaders();
        InputStream errorStream = this.conn.getErrorStream();
        this.responseStream = (errorStream != null ? errorStream : this.conn.getInputStream());
        String contentEncoding = headers.getValue(HttpHeaderConsts.CONTENT_ENCODING);
        // Used to process http content_encoding, when content_encoding is GZIP, use GZIPInputStream
        if (CONTENT_ENCODING.equals(contentEncoding)) {
            byte[] bytes = IoUtils.tryDecompress(this.responseStream);
            if (bytes == null) {
                throw new IOException("decompress http response error");
            }
            return new ByteArrayInputStream(bytes);
        }
        return this.responseStream;
    }
    
    @Override
    public int getStatusCode() throws IOException {
        return this.conn.getResponseCode();
    }
    
    @Override
    public String getStatusText() throws IOException {
        return this.conn.getResponseMessage();
    }
    
    @Override
    public void close() {
        IoUtils.closeQuietly(this.responseStream);
    }
}
