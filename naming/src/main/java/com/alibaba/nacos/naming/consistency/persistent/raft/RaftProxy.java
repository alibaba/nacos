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
package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.util.Map;

/**
 * @author nacos
 */
@Component
public class RaftProxy {

    public void proxyGET(String server, String api, Map<String, String> params) throws Exception {
        // do proxy
        if (!server.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
            server = server + UtilsAndCommons.IP_PORT_SPLITER + RunningConfig.getServerPort();
        }
        String url = "http://" + server + RunningConfig.getContextPath() + api;

        HttpClient.HttpResult result =  HttpClient.httpGet(url, null, params);
        if (result.code != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("leader failed, caused by: " + result.content);
        }
    }

    public void proxy(String server, String api, Map<String, String> params, HttpMethod method) throws Exception {
        // do proxy
        if (!server.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
            server = server + UtilsAndCommons.IP_PORT_SPLITER + RunningConfig.getServerPort();
        }
        String url = "http://" + server + RunningConfig.getContextPath() + api;
        HttpClient.HttpResult result;
        switch (method) {
            case GET:
                result =  HttpClient.httpGet(url, null, params);
                break;
            case POST:
                result = HttpClient.httpPost(url, null, params);
                break;
            case DELETE:
                result =  HttpClient.httpDelete(url, null, params);
                break;
            default:
                throw new RuntimeException("unsupported method:" + method);
        }

        if (result.code != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("leader failed, caused by: " + result.content);
        }
    }

    public void proxyPostLarge(String server, String api, String content, Map<String, String> headers) throws Exception {
        // do proxy
        if (!server.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
            server = server + UtilsAndCommons.IP_PORT_SPLITER + RunningConfig.getServerPort();
        }
        String url = "http://" + server + RunningConfig.getContextPath() + api;

        HttpClient.HttpResult result =  HttpClient.httpPostLarge(url, headers, content);
        if (result.code != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("leader failed, caused by: " + result.content);
        }
    }
}
