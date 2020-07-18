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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.UuidUtils;
import com.alibaba.nacos.common.utils.VersionUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Http tool.
 *
 * @author Nacos
 * @deprecated Use NacosRestTemplate{@link NacosRestTemplate} unified http client
 */
@Deprecated
public class HttpSimpleClient {
    
    /**
     * Get method.
     *
     * @param url           url
     * @param headers       headers
     * @param paramValues   paramValues
     * @param encoding      encoding
     * @param readTimeoutMs readTimeoutMs
     * @param isSsl         isSsl
     * @return result
     * @throws IOException io exception
     */
    public static HttpResult httpGet(String url, List<String> headers, List<String> paramValues, String encoding,
            long readTimeoutMs, boolean isSsl) throws IOException {
        String encodedContent = encodingParams(paramValues, encoding);
        url += (null == encodedContent) ? "" : ("?" + encodedContent);
        if (Limiter
                .isLimit(MD5Utils.md5Hex(new StringBuilder(url).append(encodedContent).toString(), Constants.ENCODE))) {
            return new HttpResult(NacosException.CLIENT_OVER_THRESHOLD,
                    "More than client-side current limit threshold");
        }
        
        HttpURLConnection conn = null;
        
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(ParamUtil.getConnectTimeout() > 100 ? ParamUtil.getConnectTimeout() : 100);
            conn.setReadTimeout((int) readTimeoutMs);
            List<String> newHeaders = getHeaders(url, headers, paramValues);
            setHeaders(conn, newHeaders, encoding);
            
            conn.connect();
            
            int respCode = conn.getResponseCode();
            String resp = null;
            
            if (HttpURLConnection.HTTP_OK == respCode) {
                resp = IoUtils.toString(conn.getInputStream(), encoding);
            } else {
                resp = IoUtils.toString(conn.getErrorStream(), encoding);
            }
            return new HttpResult(respCode, conn.getHeaderFields(), resp);
        } finally {
            IoUtils.closeQuietly(conn);
        }
    }
    
    /**
     * 发送GET请求.
     */
    public static HttpResult httpGet(String url, List<String> headers, List<String> paramValues, String encoding,
            long readTimeoutMs) throws IOException {
        return httpGet(url, headers, paramValues, encoding, readTimeoutMs, false);
    }
    
    /**
     * 发送POST请求.
     *
     * @param url           url
     * @param headers       请求Header，可以为null
     * @param paramValues   参数，可以为null
     * @param encoding      URL编码使用的字符集
     * @param readTimeoutMs 响应超时
     * @param isSsl         是否https
     * @return result
     * @throws IOException io exception
     */
    public static HttpResult httpPost(String url, List<String> headers, List<String> paramValues, String encoding,
            long readTimeoutMs, boolean isSsl) throws IOException {
        String encodedContent = encodingParams(paramValues, encoding);
        encodedContent = (null == encodedContent) ? "" : encodedContent;
        if (Limiter
                .isLimit(MD5Utils.md5Hex(new StringBuilder(url).append(encodedContent).toString(), Constants.ENCODE))) {
            return new HttpResult(NacosException.CLIENT_OVER_THRESHOLD,
                    "More than client-side current limit threshold");
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(ParamUtil.getConnectTimeout() > 3000 ? ParamUtil.getConnectTimeout() : 3000);
            conn.setReadTimeout((int) readTimeoutMs);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            List<String> newHeaders = getHeaders(url, headers, paramValues);
            setHeaders(conn, newHeaders, encoding);
            
            conn.getOutputStream().write(encodedContent.getBytes(encoding));
            
            int respCode = conn.getResponseCode();
            String resp = null;
            
            if (HttpURLConnection.HTTP_OK == respCode) {
                resp = IoUtils.toString(conn.getInputStream(), encoding);
            } else {
                resp = IoUtils.toString(conn.getErrorStream(), encoding);
            }
            return new HttpResult(respCode, conn.getHeaderFields(), resp);
        } finally {
            IoUtils.closeQuietly(conn);
        }
    }
    
    /**
     * 发送POST请求.
     *
     * @param url           url
     * @param headers       请求Header，可以为null
     * @param paramValues   参数，可以为null
     * @param encoding      URL编码使用的字符集
     * @param readTimeoutMs 响应超时
     * @return result
     * @throws IOException io exception
     */
    public static HttpResult httpPost(String url, List<String> headers, List<String> paramValues, String encoding,
            long readTimeoutMs) throws IOException {
        return httpPost(url, headers, paramValues, encoding, readTimeoutMs, false);
    }
    
    /**
     * Delete method.
     *
     * @param url           url
     * @param headers       请求Header，可以为null
     * @param paramValues   参数，可以为null
     * @param encoding      URL编码使用的字符集
     * @param readTimeoutMs 响应超时
     * @param isSsl         是否https
     * @return result
     * @throws IOException io exception
     */
    public static HttpResult httpDelete(String url, List<String> headers, List<String> paramValues, String encoding,
            long readTimeoutMs, boolean isSsl) throws IOException {
        String encodedContent = encodingParams(paramValues, encoding);
        url += (null == encodedContent) ? "" : ("?" + encodedContent);
        if (Limiter
                .isLimit(MD5Utils.md5Hex(new StringBuilder(url).append(encodedContent).toString(), Constants.ENCODE))) {
            return new HttpResult(NacosException.CLIENT_OVER_THRESHOLD,
                    "More than client-side current limit threshold");
        }
        
        HttpURLConnection conn = null;
        
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(ParamUtil.getConnectTimeout() > 100 ? ParamUtil.getConnectTimeout() : 100);
            conn.setReadTimeout((int) readTimeoutMs);
            List<String> newHeaders = getHeaders(url, headers, paramValues);
            setHeaders(conn, newHeaders, encoding);
            
            conn.connect();
            
            int respCode = conn.getResponseCode();
            String resp = null;
            
            if (HttpURLConnection.HTTP_OK == respCode) {
                resp = IoUtils.toString(conn.getInputStream(), encoding);
            } else {
                resp = IoUtils.toString(conn.getErrorStream(), encoding);
            }
            return new HttpResult(respCode, conn.getHeaderFields(), resp);
        } finally {
            IoUtils.closeQuietly(conn);
        }
    }
    
    /**
     * Delete method.
     *
     * @param url           url
     * @param headers       请求Header，可以为null
     * @param paramValues   参数，可以为null
     * @param encoding      URL编码使用的字符集
     * @param readTimeoutMs 响应超时
     * @return result
     * @throws IOException io exception
     */
    public static HttpResult httpDelete(String url, List<String> headers, List<String> paramValues, String encoding,
            long readTimeoutMs) throws IOException {
        return httpGet(url, headers, paramValues, encoding, readTimeoutMs, false);
    }
    
    private static void setHeaders(HttpURLConnection conn, List<String> headers, String encoding) {
        if (null != headers) {
            for (Iterator<String> iter = headers.iterator(); iter.hasNext(); ) {
                conn.addRequestProperty(iter.next(), iter.next());
            }
        }
        conn.addRequestProperty(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.version);
        conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + encoding);
        
        String ts = String.valueOf(System.currentTimeMillis());
        String token = MD5Utils.md5Hex(ts + ParamUtil.getAppKey(), Constants.ENCODE);
        
        conn.addRequestProperty(Constants.CLIENT_APPNAME_HEADER, ParamUtil.getAppName());
        conn.addRequestProperty(Constants.CLIENT_REQUEST_TS_HEADER, ts);
        conn.addRequestProperty(Constants.CLIENT_REQUEST_TOKEN_HEADER, token);
    }
    
    private static List<String> getHeaders(String url, List<String> headers, List<String> paramValues)
            throws IOException {
        List<String> newHeaders = new ArrayList<String>();
        newHeaders.add("exConfigInfo");
        newHeaders.add("true");
        newHeaders.add("RequestId");
        newHeaders.add(UuidUtils.generateUuid());
        if (headers != null) {
            newHeaders.addAll(headers);
        }
        return newHeaders;
    }
    
    private static String encodingParams(List<String> paramValues, String encoding)
            throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        if (null == paramValues) {
            return null;
        }
        
        for (Iterator<String> iter = paramValues.iterator(); iter.hasNext(); ) {
            sb.append(iter.next()).append("=");
            sb.append(URLEncoder.encode(iter.next(), encoding));
            if (iter.hasNext()) {
                sb.append("&");
            }
        }
        return sb.toString();
    }
    
    public static class HttpResult {
        
        public final int code;
        
        public final Map<String, List<String>> headers;
        
        public final String content;
        
        public HttpResult(int code, String content) {
            this.code = code;
            this.headers = null;
            this.content = content;
        }
        
        public HttpResult(int code, Map<String, List<String>> headers, String content) {
            this.code = code;
            this.headers = headers;
            this.content = content;
        }
        
        @Override
        public String toString() {
            return "HttpResult{" + "code=" + code + ", headers=" + headers + ", content='" + content + '\'' + '}';
        }
    }
    
}
