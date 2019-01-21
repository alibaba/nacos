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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.naming.boot.RunningConfig;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nacos
 */
public class NamingProxy {

    private static final String DATA_SYNC_URL = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/partition/onSync";

    private static final String TIMESTAMP_SYNC_URL = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/partition/syncTimestamps";

    public static boolean syncTimestamps(Map<String, Long> timestamps, String server) {

        try {
            Map<String, String> headers = new HashMap<>(128);

            headers.put("Client-Version", UtilsAndCommons.SERVER_VERSION);
            headers.put("Connection", "Keep-Alive");

            HttpClient.HttpResult result = HttpClient.httpPutLarge("http://" + server + RunningConfig.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + TIMESTAMP_SYNC_URL, headers, JSON.toJSONBytes(timestamps));

            if (HttpURLConnection.HTTP_OK == result.code) {
                return true;
            }

            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.code) {
                return true;
            }

            throw new IOException("failed to req API:" + "http://" + server
                + RunningConfig.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + TIMESTAMP_SYNC_URL + ". code:"
                + result.code + " msg: " + result.content);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return false;
    }

    public static boolean syncData(byte[] data, String curServer) throws Exception {
        try {
            Map<String, String> headers = new HashMap<>(128);

            headers.put("Client-Version", UtilsAndCommons.SERVER_VERSION);
            headers.put("Accept-Encoding", "gzip,deflate,sdch");
            headers.put("Connection", "Keep-Alive");
            headers.put("Content-Encoding", "gzip");

            HttpClient.HttpResult result = HttpClient.httpPutLarge("http://" + curServer + RunningConfig.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_SYNC_URL, headers, data);

            if (HttpURLConnection.HTTP_OK == result.code) {
                return true;
            }

            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.code) {
                return true;
            }

            throw new IOException("failed to req API:" + "http://" + curServer
                + RunningConfig.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_SYNC_URL + ". code:"
                + result.code + " msg: " + result.content);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return false;
    }

    public static String reqAPI(String api, Map<String, String> params, String curServer, boolean isPost) throws Exception {
        try {
            List<String> headers = Arrays.asList("Client-Version", UtilsAndCommons.SERVER_VERSION,
                "Accept-Encoding", "gzip,deflate,sdch",
                "Connection", "Keep-Alive",
                "Content-Encoding", "gzip");


            HttpClient.HttpResult result;

            if (!curServer.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
                curServer = curServer + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
            }

            if (isPost) {
                result = HttpClient.httpPost("http://" + curServer + RunningConfig.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api, headers, params);
            } else {
                result = HttpClient.httpGet("http://" + curServer + RunningConfig.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api, headers, params);
            }

            if (HttpURLConnection.HTTP_OK == result.code) {
                return result.content;
            }

            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.code) {
                return StringUtils.EMPTY;
            }

            throw new IOException("failed to req API:" + "http://" + curServer
                + RunningConfig.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api + ". code:"
                + result.code + " msg: " + result.content);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return StringUtils.EMPTY;
    }
}
