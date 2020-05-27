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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.IoUtils;
import com.google.common.net.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * {@link ClientHttpRequest} implementation that uses standard JDK facilities to
 * execute streaming requests
 *
 * @author mai.jh
 * @date 2020/5/24
 */
public class DefaultSimpleClientHttpRequest implements ClientHttpRequest {

    private static final Logger logger = LoggerFactory.getLogger(NacosRestTemplate.class);

    private int connectTimeout = 3000;

    private int readTimeout = 50000;


    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    @Override
    public ClientHttpResponse execute(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity) throws IOException {
        Header headers = requestHttpEntity.getHeaders();
        // open URL connection
        HttpURLConnection conn = openConnection(uri.toURL());
        prepareConnection(conn, httpMethod);
        addHeaders(conn, headers);
        if (conn.getDoOutput() && !requestHttpEntity.isEmptyBody()) {
            byte[] body = requestHttpEntity.getBody();
            conn.setFixedLengthStreamingMode(body.length);
            IoUtils.copy(body, conn.getOutputStream());
        }
        conn.connect();
        if (logger.isDebugEnabled()) {
            logger.debug("Request from server: " + conn.getURL());
        }
        return handleResponse(conn);
    }

    private ClientHttpResponse handleResponse(HttpURLConnection conn) throws IOException{
        int responseCode = conn.getResponseCode();
        String responseMessage = conn.getResponseMessage();
        Header responseHeader = Header.newInstance();
        InputStream inputStream = conn.getErrorStream();
        inputStream = inputStream != null ? inputStream : conn.getInputStream();

        for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
            responseHeader.addParam(entry.getKey(), entry.getValue().get(0));
        }

        return new DefaultClientHttpResponse(responseHeader,responseCode, responseMessage, inputStream);
    }

    private HttpURLConnection openConnection(URL url) throws IOException{
        URLConnection urlConnection = url.openConnection();
        if (!HttpURLConnection.class.isInstance(urlConnection)) {
            throw new IllegalStateException("HttpURLConnection required for [" + url + "] but got: " + urlConnection);
        }
        return (HttpURLConnection) urlConnection;
    }

    private void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException{
        connection.setRequestMethod(httpMethod);
        connection.setDoInput(true);
        if (this.connectTimeout >= 0) {
            connection.setConnectTimeout(this.connectTimeout);
        }
        if (this.readTimeout >= 0) {
            connection.setReadTimeout(this.readTimeout);
        }
        if (HttpMethod.POST.equals(httpMethod) || HttpMethod.PUT.equals(httpMethod) ||
            HttpMethod.PATCH.equals(httpMethod) || HttpMethod.DELETE.equals(httpMethod)) {
            connection.setDoOutput(true);
        } else {
            connection.setDoOutput(false);
        }


    }


    private void addHeaders(HttpURLConnection connection, Header headers) {
        Set<Map.Entry<String, String>> entrySet = headers.getHeader().entrySet();
        for (Map.Entry<String, String> entry : entrySet) {
            connection.addRequestProperty(entry.getKey(), entry.getValue());
        }
        connection.addRequestProperty("Accept-Charset", Constants.ENCODE);

    }
}
