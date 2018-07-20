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
package com.alibaba.nacos.naming.raft;

import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;

import java.net.HttpURLConnection;
import java.util.Map;

/**
 * @author nacos
 */
public class RaftProxy {
    public static void proxyGET(String api, Map<String, String> params) throws Exception {
        if (RaftCore.isLeader()) {
            throw new IllegalStateException("I'm leader, no need to do proxy");
        }

        if (RaftCore.getLeader() == null) {
            throw new IllegalStateException("No leader at present");
        }

        // do proxy
        String server = RaftCore.getLeader().ip;
        if (!server.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
            server = server + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
        }
        String url = "http://" + server + RunningConfig.getContextPath() + api;

        HttpClient.HttpResult result =  HttpClient.httpGet(url, null, params);
        if (result.code != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("leader failed, caused by: " + result.content);
        }
    }

    public static void proxyPostLarge(String api, String content, Map<String, String> headers) throws Exception {
        if (RaftCore.isLeader()) {
            throw new IllegalStateException("I'm leader, no need to do proxy");
        }

        if (RaftCore.getLeader() == null) {
            throw new IllegalStateException("No leader at present");
        }

        // do proxy
        String server = RaftCore.getLeader().ip;
        if (!server.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
            server = server + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
        }
        String url = "http://" + server + RunningConfig.getContextPath() + api;

        HttpClient.HttpResult result =  HttpClient.httpPostLarge(url, headers, content);
        if (result.code != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("leader failed, caused by: " + result.content);
        }
    }
}
