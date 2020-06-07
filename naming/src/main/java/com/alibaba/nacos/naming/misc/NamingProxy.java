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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.*;

/**
 * @author nacos
 */
public class NamingProxy {

    private static final String DATA_ON_SYNC_URL = "/distro/datum";

    private static final String DATA_GET_URL = "/distro/datum";

    private static final String ALL_DATA_GET_URL = "/distro/datums";

    private static final String TIMESTAMP_SYNC_URL = "/distro/checksum";

    public static void syncCheckSums(Map<String, String> checksumMap, String server) {

        try {
            Map<String, String> headers = new HashMap<>(128);

            headers.put(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.VERSION);
            headers.put(HttpHeaderConsts.USER_AGENT_HEADER, UtilsAndCommons.SERVER_VERSION);
            headers.put("Connection", "Keep-Alive");

            HttpClient.asyncHttpPutLarge("http://" + server + ApplicationUtils.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + TIMESTAMP_SYNC_URL + "?source=" + NetUtils.localServer(),
                headers, JacksonUtils.toJsonBytes(checksumMap),
                new AsyncCompletionHandler() {
                    @Override
                    public Object onCompleted(Response response) throws Exception {
                        if (HttpURLConnection.HTTP_OK != response.getStatusCode()) {
                            Loggers.DISTRO.error("failed to req API: {}, code: {}, msg: {}",
                                "http://" + server + ApplicationUtils.getContextPath() +
                                    UtilsAndCommons.NACOS_NAMING_CONTEXT + TIMESTAMP_SYNC_URL,
                                response.getStatusCode(), response.getResponseBody());
                        }
                        return null;
                    }

                    @Override
                    public void onThrowable(Throwable t) {
                        Loggers.DISTRO.error("failed to req API:" + "http://" + server
                            + ApplicationUtils.getContextPath()
                            + UtilsAndCommons.NACOS_NAMING_CONTEXT + TIMESTAMP_SYNC_URL, t);
                    }
                });
        } catch (Exception e) {
            Loggers.DISTRO.warn("NamingProxy", e);
        }
    }

    public static byte[] getData(List<String> keys, String server) throws Exception {

        Map<String, String> params = new HashMap<>(8);
        params.put("keys", StringUtils.join(keys, ","));
        HttpClient.HttpResult result = HttpClient.httpGetLarge("http://" + server + ApplicationUtils.getContextPath()
            + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_GET_URL, new HashMap<>(8), JacksonUtils.toJson(params));

        if (HttpURLConnection.HTTP_OK == result.code) {
            return result.content.getBytes();
        }

        throw new IOException("failed to req API: " + "http://" + server
            + ApplicationUtils.getContextPath()
            + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_GET_URL + ". code: "
            + result.code + " msg: " + result.content);
    }

    public static byte[] getAllData(String server) throws Exception {

        Map<String, String> params = new HashMap<>(8);
        HttpClient.HttpResult result = HttpClient.httpGet("http://" + server + ApplicationUtils.getContextPath()
            + UtilsAndCommons.NACOS_NAMING_CONTEXT + ALL_DATA_GET_URL, new ArrayList<>(), params);

        if (HttpURLConnection.HTTP_OK == result.code) {
            return result.content.getBytes();
        }

        throw new IOException("failed to req API: " + "http://" + server
            + ApplicationUtils.getContextPath()
            + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_GET_URL + ". code: "
            + result.code + " msg: " + result.content);
    }


    public static boolean syncData(byte[] data, String curServer) {
        Map<String, String> headers = new HashMap<>(128);

        headers.put(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.VERSION);
        headers.put(HttpHeaderConsts.USER_AGENT_HEADER, UtilsAndCommons.SERVER_VERSION);
        headers.put("Accept-Encoding", "gzip,deflate,sdch");
        headers.put("Connection", "Keep-Alive");
        headers.put("Content-Encoding", "gzip");

        try {
            HttpClient.HttpResult result = HttpClient.httpPutLarge("http://" + curServer + ApplicationUtils.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_ON_SYNC_URL, headers, data);
            if (HttpURLConnection.HTTP_OK == result.code) {
                return true;
            }
            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.code) {
                return true;
            }
            throw new IOException("failed to req API:" + "http://" + curServer
                + ApplicationUtils.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_ON_SYNC_URL + ". code:"
                + result.code + " msg: " + result.content);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return false;
    }

    public static String reqAPI(String api, Map<String, String> params, String curServer) throws Exception {
        try {
            List<String> headers = Arrays.asList(
                HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.VERSION,
                HttpHeaderConsts.USER_AGENT_HEADER, UtilsAndCommons.SERVER_VERSION,
                "Accept-Encoding", "gzip,deflate,sdch",
                "Connection", "Keep-Alive",
                "Content-Encoding", "gzip");


            HttpClient.HttpResult result;

            if (!curServer.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
                curServer = curServer + UtilsAndCommons.IP_PORT_SPLITER + ApplicationUtils.getPort();
            }


            result = HttpClient.httpGet("http://" + curServer + api, headers, params);

            if (HttpURLConnection.HTTP_OK == result.code) {
                return result.content;
            }

            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.code) {
                return StringUtils.EMPTY;
            }

            throw new IOException("failed to req API:" + "http://" + curServer + api + ". code:"
                + result.code + " msg: " + result.content);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return StringUtils.EMPTY;
    }

    public static String reqAPI(String api, Map<String, String> params, String curServer, boolean isPost) throws Exception {
        try {
            List<String> headers = Arrays.asList(
                HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.VERSION,
                HttpHeaderConsts.USER_AGENT_HEADER, UtilsAndCommons.SERVER_VERSION,
                "Accept-Encoding", "gzip,deflate,sdch",
                "Connection", "Keep-Alive",
                "Content-Encoding", "gzip");


            HttpClient.HttpResult result;

            if (!curServer.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
                curServer = curServer + UtilsAndCommons.IP_PORT_SPLITER + ApplicationUtils.getPort();
            }

            if (isPost) {
                result = HttpClient.httpPost("http://" + curServer + ApplicationUtils.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api, headers, params);
            } else {
                result = HttpClient.httpGet("http://" + curServer + ApplicationUtils.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api, headers, params);
            }

            if (HttpURLConnection.HTTP_OK == result.code) {
                return result.content;
            }

            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.code) {
                return StringUtils.EMPTY;
            }

            throw new IOException("failed to req API:" + "http://" + curServer
                + ApplicationUtils.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api + ". code:"
                + result.code + " msg: " + result.content);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return StringUtils.EMPTY;
    }

    public static String reqCommon(String path, Map<String, String> params, String curServer, boolean isPost) throws Exception {
        try {
            List<String> headers = Arrays.asList("Client-Version", UtilsAndCommons.SERVER_VERSION,
                "User-Agent", UtilsAndCommons.SERVER_VERSION,
                "Accept-Encoding", "gzip,deflate,sdch",
                "Connection", "Keep-Alive",
                "Content-Encoding", "gzip");


            HttpClient.HttpResult result;

            if (!curServer.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
                curServer = curServer + UtilsAndCommons.IP_PORT_SPLITER + ApplicationUtils.getPort();
            }

            if (isPost) {
                result = HttpClient.httpPost("http://" + curServer + ApplicationUtils.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + path, headers, params);
            } else {
                result = HttpClient.httpGet("http://" + curServer + ApplicationUtils.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + path, headers, params);
            }

            if (HttpURLConnection.HTTP_OK == result.code) {
                return result.content;
            }

            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.code) {
                return StringUtils.EMPTY;
            }

            throw new IOException("failed to req API:" + "http://" + curServer
                + ApplicationUtils.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + path + ". code:"
                + result.code + " msg: " + result.content);
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

        public String toUrl() {
            StringBuilder sb = new StringBuilder();
            for (String key : params.keySet()) {
                sb.append(key).append("=").append(params.get(key)).append("&");
            }
            return sb.toString();
        }
    }
}
