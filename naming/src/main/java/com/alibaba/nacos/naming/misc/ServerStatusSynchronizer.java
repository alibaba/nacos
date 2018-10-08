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

import com.alibaba.nacos.naming.boot.RunningConfig;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nacos
 */
public class ServerStatusSynchronizer implements Synchronizer {
    @Override
    public void send(final String serverIP, Message msg) {
        if(serverIP == null) {
            return;
        }

        final Map<String,String> params = new HashMap<String, String>(2);

        params.put("serverStatus", msg.getData());

        String url = "http://" + serverIP + ":" + RunningConfig.getServerPort()
                + RunningConfig.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/serverStatus";

        if (serverIP.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
            url = "http://" + serverIP + RunningConfig.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT
                    + "/api/serverStatus";
        }

        try {
            HttpClient.asyncHttpGet(url, null, params, new AsyncCompletionHandler() {
                @Override
                public Integer onCompleted(Response response) throws Exception {
                    if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                        Loggers.SRV_LOG.warn("STATUS-SYNCHRONIZE", "failed to request serverStatus, remote server: " + serverIP);

                        return 1;
                    }
                    return 0;
                }
            });
       } catch (Exception e) {
            Loggers.SRV_LOG.warn("STATUS-SYNCHRONIZE", "failed to request serverStatus, remote server: " + serverIP, e);
        }
    }

    @Override
    public Message get(String server, String key) {
        return null;
    }
}
