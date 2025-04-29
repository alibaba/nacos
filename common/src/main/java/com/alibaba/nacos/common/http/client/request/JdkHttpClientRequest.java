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

package com.alibaba.nacos.common.http.client.request;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.client.response.JdkHttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * JDK http client request implement.
 *
 * @author mai.jh
 */
public class JdkHttpClientRequest implements HttpClientRequest {
    
    private static final String CONTENT_LENGTH = "Content-Length";
    
    private static final String BOUNDARY_PREFIX = "----WebKitFormBoundary";
    
    private static final String LINE_FEED = "\r\n";
    
    private HttpClientConfig httpClientConfig;
    
    public JdkHttpClientRequest(HttpClientConfig httpClientConfig) {
        this.httpClientConfig = httpClientConfig;
    }
    
    /**
     * Use specified {@link SSLContext}.
     *
     * @param sslContext ssl context
     */
    @SuppressWarnings("checkstyle:abbreviationaswordinname")
    public void setSslContext(SSLContext sslContext) {
        if (sslContext != null) {
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        }
    }
    
    /**
     * Replace the default HostnameVerifier.
     *
     * @param hostnameVerifier custom hostnameVerifier
     */
    @SuppressWarnings("checkstyle:abbreviationaswordinname")
    public void replaceSslHostnameVerifier(HostnameVerifier hostnameVerifier) {
        if (hostnameVerifier != null) {
            HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
        }
    }
    
    @Override
    public HttpClientResponse execute(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity)
            throws Exception {
        final Object body = requestHttpEntity.getBody();
        final Header headers = requestHttpEntity.getHeaders();
        replaceDefaultConfig(requestHttpEntity.getHttpClientConfig());
        
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        Map<String, String> headerMap = headers.getHeader();
        if (headerMap != null && headerMap.size() > 0) {
            for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        
        conn.setConnectTimeout(this.httpClientConfig.getConTimeOutMillis());
        conn.setReadTimeout(this.httpClientConfig.getReadTimeOutMillis());
        conn.setRequestMethod(httpMethod);
        if (body != null && !"".equals(body)) {
            if (body instanceof File) {
                handleFileUpload(conn, (File) body);
            }
            String contentType = headers.getValue(HttpHeaderConsts.CONTENT_TYPE);
            String bodyStr = body instanceof String ? (String) body : JacksonUtils.toJson(body);
            if (MediaType.APPLICATION_FORM_URLENCODED.equals(contentType)) {
                Map<String, String> map = JacksonUtils.toObj(bodyStr, HashMap.class);
                bodyStr = HttpUtils.encodingParams(map, headers.getCharset());
            }
            if (bodyStr != null) {
                conn.setDoOutput(true);
                byte[] b = bodyStr.getBytes();
                conn.setRequestProperty(CONTENT_LENGTH, String.valueOf(b.length));
                OutputStream outputStream = conn.getOutputStream();
                outputStream.write(b, 0, b.length);
                outputStream.flush();
                IoUtils.closeQuietly(outputStream);
            }
        }
        conn.connect();
        return new JdkHttpClientResponse(conn);
    }
    
    private void handleFileUpload(HttpURLConnection conn, File file) throws IOException {
        String boundary = BOUNDARY_PREFIX + System.currentTimeMillis();
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append(LINE_FEED);
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"")
                .append(LINE_FEED);
        sb.append("Content-Type: ").append(Files.probeContentType(file.toPath())).append(LINE_FEED).append(LINE_FEED);
        
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        byte[] boundaryBytes = (LINE_FEED + "--" + boundary + "--" + LINE_FEED).getBytes();
        
        conn.setDoOutput(true);
        try (OutputStream outputStream = conn.getOutputStream()) {
            outputStream.write(sb.toString().getBytes());
            outputStream.write(fileBytes);
            outputStream.write(boundaryBytes);
            outputStream.flush();
        }
    }
    
    /**
     * Replace the HTTP config created by default with the HTTP config specified in the request.
     *
     * @param replaceConfig http config
     */
    private void replaceDefaultConfig(HttpClientConfig replaceConfig) {
        if (replaceConfig == null) {
            return;
        }
        this.httpClientConfig = replaceConfig;
    }
    
    @Override
    public void close() throws IOException {
    
    }
}
