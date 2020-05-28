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

package com.alibaba.nacos.common.http.client;

import com.alibaba.nacos.common.http.param.Header;
import com.sun.xml.internal.ws.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.io.InputStream;


/**
 * DefaultClientHttpResponse implementation {@link HttpClientResponse}
 *
 * @author mai.jh
 * @date 2020/5/25
 */
public class DefaultClientHttpResponse implements HttpClientResponse {

    private Header header;

    private int responseCode;

    private String responseCodeMessage;

    private InputStream responseBody;

    public DefaultClientHttpResponse(Header header, int responseCode, String responseCodeMessage, InputStream responseBody) {
        this.header = header;
        this.responseCode = responseCode;
        this.responseCodeMessage = responseCodeMessage;
        this.responseBody = responseBody;
    }

    @Override
    public int getStatusCode() {
        return responseCode;
    }

    @Override
    public String getStatusText() {
        return responseCodeMessage;
    }

    @Override
    public Header getHeaders() {
        return header;
    }

    @Override
    public InputStream getBody() throws IOException{
        return this.responseBody;
    }

    @Override
    public void close() {
        try {
            if (this.responseBody != null) {
                this.responseBody.close();
            }
        }
        catch (Exception ex) {
            // ignore
        }
    }
}
