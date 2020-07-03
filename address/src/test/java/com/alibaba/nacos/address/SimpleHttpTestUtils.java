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

package com.alibaba.nacos.address;

import com.alibaba.nacos.common.utils.IoUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

public class SimpleHttpTestUtils {
    
    private static final String REQUEST_METHOD_DELETE = "DELETE";
    
    private static final String REQUEST_METHOD_PUT = "PUT";
    
    private static final String REQUEST_METHOD_POST = "POST";
    
    private static final String REQUEST_METHOD_GET = "GET";
    
    /**
     * 连接超时.
     */
    private static final int CONNECT_TIME_OUT = 2000;
    
    /**
     * 读取数据超时.
     */
    private static final int READ_TIME_OUT = 2000;
    
    /**
     * 请求编码.
     */
    public static final String REQUEST_ENCODING = "UTF-8";
    
    /**
     * 接收编码.
     */
    public static final String RESPONSE_ENCODING = "UTF-8";
    
    public static final short OK = 200;
    
    public static final short BAD_REQUEST = 400;
    
    public static final short INTERNAL_SERVER_ERROR = 500;
    
    public static final short PARAM_ERROR_NO_ANALYSESOR = 1000;
    
    /**
     * 发送带参数的GET的HTTP请求.
     *
     * @param reqUrl   HTTP请求URL
     * @param paramMap 参数映射表
     * @return HTTP响应的字符串
     */
    public static String doGet(String reqUrl, Map<String, String> paramMap, String recvEncoding) {
        return doRequest(reqUrl, paramMap, REQUEST_METHOD_GET, recvEncoding);
    }
    
    /**
     * 发送带参数的POST的HTTP请求.
     *
     * @param reqUrl   HTTP请求URL
     * @param paramMap 参数映射表
     * @return HTTP响应的字符串
     */
    public static String doPost(String reqUrl, Map<String, String> paramMap, String recvEncoding) {
        return doRequest(reqUrl, paramMap, REQUEST_METHOD_POST, recvEncoding);
    }
    
    /**
     * 发送带参数的 PUT 的 HTTP 请求.
     *
     * @param reqUrl   HTTP请求URL
     * @param paramMap 参数映射表
     * @return HTTP响应的字符串
     */
    public static String doPut(String reqUrl, Map<String, String> paramMap, String recvEncoding) {
        return doRequest(reqUrl, paramMap, REQUEST_METHOD_PUT, recvEncoding);
    }
    
    /**
     * 发送带参数的 DELETE 的 HTTP 请求.
     *
     * @param reqUrl   HTTP请求URL
     * @param paramMap 参数映射表
     * @return HTTP响应的字符串
     */
    public static String doDelete(String reqUrl, Map<String, String> paramMap, String recvEncoding) {
        return doRequest(reqUrl, paramMap, REQUEST_METHOD_DELETE, recvEncoding);
    }
    
    private static String doRequest(String reqUrl, Map<String, String> paramMap, String reqMethod,
            String recvEncoding) {
        
        return doExecute(reqUrl, paramMap, reqMethod, recvEncoding);
    }
    
    private static String doExecute(String reqUrl, Map<String, String> paramMap, String reqMethod,
            String recvEncoding) {
        HttpURLConnection urlCon = null;
        String responseContent = null;
        try {
            StringBuilder params = new StringBuilder();
            if (paramMap != null) {
                for (Map.Entry<String, String> element : paramMap.entrySet()) {
                    params.append(element.getKey());
                    params.append("=");
                    params.append(URLEncoder.encode(element.getValue(), REQUEST_ENCODING));
                    params.append("&");
                }
                
                if (params.length() > 0) {
                    params = params.deleteCharAt(params.length() - 1);
                }
                
                if (params.length() > 0 && (REQUEST_METHOD_GET.equals(reqMethod) || REQUEST_METHOD_DELETE
                        .equals(reqMethod))) {
                    reqUrl = reqUrl + "?" + params.toString();
                }
            }
            URL url = new URL(reqUrl);
            urlCon = (HttpURLConnection) url.openConnection();
            urlCon.setRequestMethod(reqMethod);
            urlCon.setConnectTimeout(CONNECT_TIME_OUT);
            urlCon.setReadTimeout(READ_TIME_OUT);
            urlCon.setDoOutput(true);
            if (REQUEST_METHOD_POST.equals(reqMethod) || REQUEST_METHOD_PUT.equals(reqMethod)) {
                byte[] b = params.toString().getBytes();
                urlCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
                urlCon.setRequestProperty("Content-Length", String.valueOf(b.length));
                urlCon.getOutputStream().write(b, 0, b.length);
                urlCon.getOutputStream().flush();
                urlCon.getOutputStream().close();
            }
            InputStream in = urlCon.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(in, recvEncoding));
            String tempLine = rd.readLine();
            StringBuffer tempStr = new StringBuffer();
            while (tempLine != null) {
                tempStr.append(tempLine);
                tempLine = rd.readLine();
            }
            responseContent = tempStr.toString();
            rd.close();
            in.close();
            
            urlCon.getResponseMessage();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IoUtils.closeQuietly(urlCon);
        }
        return responseContent;
    }
    
}
