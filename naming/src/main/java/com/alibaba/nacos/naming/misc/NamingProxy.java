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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Naming http proxy.
 *
 * @author nacos
 */
public class NamingProxy {
    
    private static final String DATA_ON_SYNC_URL = "/distro/datum";
    
    private static final String DATA_GET_URL = "/distro/datum";
    
    private static final String ALL_DATA_GET_URL = "/distro/datums";
    
    private static final String TIMESTAMP_SYNC_URL = "/distro/checksum";
    
    /**
     * Synchronize check sums.
     *
     * @param checksumMap checksum map
     * @param server      server address
     */
    public static void syncCheckSums(Map<String, String> checksumMap, String server) {
        syncCheckSums(JacksonUtils.toJsonBytes(checksumMap), server);
    }
    
    /**
     * Synchronize check sums.
     *
     * @param checksums checksum map bytes
     * @param server    server address
     */
    public static void syncCheckSums(byte[] checksums, String server) {
        try {
            Map<String, String> headers = new HashMap<>(128);
            
            headers.put(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.version);
            headers.put(HttpHeaderConsts.USER_AGENT_HEADER, UtilsAndCommons.SERVER_VERSION);
            headers.put(HttpHeaderConsts.CONNECTION, "Keep-Alive");
            
            HttpClient.asyncHttpPutLarge(
                    "http://" + server + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT
                            + TIMESTAMP_SYNC_URL + "?source=" + NetUtils.localServer(), headers, checksums,
                    new Callback<String>() {
                        @Override
                        public void onReceive(RestResult<String> result) {
                            if (!result.ok()) {
                                Loggers.DISTRO.error("failed to req API: {}, code: {}, msg: {}",
                                        "http://" + server + EnvUtil.getContextPath()
                                                + UtilsAndCommons.NACOS_NAMING_CONTEXT + TIMESTAMP_SYNC_URL,
                                        result.getCode(), result.getMessage());
                            }
                        }
                        
                        @Override
                        public void onError(Throwable throwable) {
                            Loggers.DISTRO.error("failed to req API:" + "http://" + server + EnvUtil.getContextPath()
                                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + TIMESTAMP_SYNC_URL, throwable);
                        }
                        
                        @Override
                        public void onCancel() {
                        
                        }
                    });
        } catch (Exception e) {
            Loggers.DISTRO.warn("NamingProxy", e);
        }
    }
    
    /**
     * Get Data from other server.
     *
     * @param keys   keys of datum
     * @param server target server address
     * @return datum byte array
     * @throws Exception exception
     */
    public static byte[] getData(List<String> keys, String server) throws Exception {
        
        Map<String, String> params = new HashMap<>(8);
        params.put("keys", StringUtils.join(keys, ","));
        RestResult<String> result = HttpClient.httpGetLarge(
                "http://" + server + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_GET_URL,
                new HashMap<>(8), JacksonUtils.toJson(params));
        
        if (result.ok()) {
            return result.getData().getBytes();
        }
        
        throw new IOException("failed to req API: " + "http://" + server + EnvUtil.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_GET_URL + ". code: " + result.getCode() + " msg: "
                + result.getMessage());
    }
    
    /**
     * Get all datum from target server.
     *
     * @param server target server address
     * @return all datum byte array
     * @throws Exception exception
     */
    public static byte[] getAllData(String server) throws Exception {
        
        Map<String, String> params = new HashMap<>(8);
        RestResult<String> result = HttpClient.httpGet(
                "http://" + server + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + ALL_DATA_GET_URL,
                new ArrayList<>(), params);
        
        if (result.ok()) {
            return result.getData().getBytes();
        }
        
        throw new IOException("failed to req API: " + "http://" + server + EnvUtil.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + ALL_DATA_GET_URL + ". code: " + result.getCode() + " msg: "
                + result.getMessage());
    }
    
    /**
     * Synchronize datum to target server.
     *
     * @param data      datum
     * @param curServer target server address
     * @return true if sync successfully, otherwise false
     */
    public static boolean syncData(byte[] data, String curServer) {
        Map<String, String> headers = new HashMap<>(128);
        
        headers.put(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.version);
        headers.put(HttpHeaderConsts.USER_AGENT_HEADER, UtilsAndCommons.SERVER_VERSION);
        headers.put(HttpHeaderConsts.ACCEPT_ENCODING, "gzip,deflate,sdch");
        headers.put(HttpHeaderConsts.CONNECTION, "Keep-Alive");
        headers.put(HttpHeaderConsts.CONTENT_ENCODING, "gzip");
        
        try {
            RestResult<String> result = HttpClient.httpPutLarge(
                    "http://" + curServer + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT
                            + DATA_ON_SYNC_URL, headers, data);
            if (result.ok()) {
                return true;
            }
            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.getCode()) {
                return true;
            }
            throw new IOException("failed to req API:" + "http://" + curServer + EnvUtil.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_ON_SYNC_URL + ". code:" + result.getCode() + " msg: "
                    + result.getData());
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return false;
    }
    
    /**
     * request api.
     *
     * @param api       api path
     * @param params    parameters of api
     * @param curServer target server address
     * @return content if request successfully and response has content, otherwise {@link StringUtils#EMPTY}
     * @throws Exception exception
     */
    public static String reqApi(String api, Map<String, String> params, String curServer) throws Exception {
        try {
            List<String> headers = Arrays.asList(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.version,
                    HttpHeaderConsts.USER_AGENT_HEADER, UtilsAndCommons.SERVER_VERSION, "Accept-Encoding",
                    "gzip,deflate,sdch", "Connection", "Keep-Alive", "Content-Encoding", "gzip");
            
            RestResult<String> result;
            
            if (!InternetAddressUtil.containsPort(curServer)) {
                curServer = curServer + InternetAddressUtil.IP_PORT_SPLITER + EnvUtil.getPort();
            }
            
            result = HttpClient.httpGet("http://" + curServer + api, headers, params);
            
            if (result.ok()) {
                return result.getData();
            }
            
            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.getCode()) {
                return StringUtils.EMPTY;
            }
            
            throw new IOException(
                    "failed to req API:" + "http://" + curServer + api + ". code:" + result.getCode() + " msg: "
                            + result.getMessage());
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return StringUtils.EMPTY;
    }
    
    /**
     * request api.
     *
     * @param api       api path
     * @param params    parameters of api
     * @param curServer target server address
     * @param isPost    whether use post method to request
     * @return content if request successfully and response has content, otherwise {@link StringUtils#EMPTY}
     * @throws Exception exception
     */
    public static String reqApi(String api, Map<String, String> params, String curServer, boolean isPost)
            throws Exception {
        try {
            List<String> headers = Arrays.asList(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.version,
                    HttpHeaderConsts.USER_AGENT_HEADER, UtilsAndCommons.SERVER_VERSION, "Accept-Encoding",
                    "gzip,deflate,sdch", "Connection", "Keep-Alive", "Content-Encoding", "gzip");
            
            RestResult<String> result;
            
            if (!InternetAddressUtil.containsPort(curServer)) {
                curServer = curServer + InternetAddressUtil.IP_PORT_SPLITER + EnvUtil.getPort();
            }
            
            if (isPost) {
                result = HttpClient.httpPost(
                        "http://" + curServer + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT
                                + "/api/" + api, headers, params);
            } else {
                result = HttpClient.httpGet(
                        "http://" + curServer + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT
                                + "/api/" + api, headers, params);
            }
            
            if (result.ok()) {
                return result.getData();
            }
            
            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.getCode()) {
                return StringUtils.EMPTY;
            }
            
            throw new IOException("failed to req API:" + "http://" + curServer + EnvUtil.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api + ". code:" + result.getCode() + " msg: "
                    + result.getMessage());
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return StringUtils.EMPTY;
    }
    
    /**
     * request api with common way.
     *
     * @param path      api path
     * @param params    parameters
     * @param curServer target server address
     * @param isPost    whether use post method to request
     * @return content if request successfully and response has content, otherwise {@link StringUtils#EMPTY}
     * @throws Exception exception
     */
    public static String reqCommon(String path, Map<String, String> params, String curServer, boolean isPost)
            throws Exception {
        try {
            List<String> headers = Arrays.asList("Client-Version", UtilsAndCommons.SERVER_VERSION, "User-Agent",
                    UtilsAndCommons.SERVER_VERSION, "Accept-Encoding", "gzip,deflate,sdch", "Connection", "Keep-Alive",
                    "Content-Encoding", "gzip");
            
            RestResult<String> result;
            
            if (!InternetAddressUtil.containsPort(curServer)) {
                curServer = curServer + InternetAddressUtil.IP_PORT_SPLITER + EnvUtil.getPort();
            }
            
            if (isPost) {
                result = HttpClient.httpPost(
                        "http://" + curServer + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + path,
                        headers, params);
            } else {
                result = HttpClient.httpGet(
                        "http://" + curServer + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + path,
                        headers, params);
            }
            
            if (result.ok()) {
                return result.getData();
            }
            
            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.getCode()) {
                return StringUtils.EMPTY;
            }
            
            throw new IOException("failed to req API:" + "http://" + curServer + EnvUtil.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + path + ". code:" + result.getCode() + " msg: " + result
                    .getMessage());
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return StringUtils.EMPTY;
    }
    
    public static class Request {
        
        private Map<String, String> params = new HashMap<>(8);
        
        public static Request newRequest() {
            return new Request();
        }
        
        public Request appendParam(String key, String value) {
            params.put(key, value);
            return this;
        }
        
        /**
         * Transfer to Url string.
         *
         * @return request url string
         */
        public String toUrl() {
            StringBuilder sb = new StringBuilder();
            for (String key : params.keySet()) {
                sb.append(key).append("=").append(params.get(key)).append("&");
            }
            return sb.toString();
        }
    }
}
