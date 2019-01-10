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
import org.apache.commons.lang3.StringUtils;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nacos
 */
public class DomainStatusSynchronizer implements Synchronizer {
    @Override
    public void send(final String serverIP, Message msg) {
        if(serverIP == null) {
            return;
        }

        Map<String,String> params = new HashMap<String, String>(10);

        params.put("domsStatus", msg.getData());
        params.put("clientIP", NetUtils.localServer());


        String url = "http://" + serverIP + ":" + RunningConfig.getServerPort() + RunningConfig.getContextPath() +
                UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/domStatus";

        if (serverIP.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
            url = "http://" + serverIP + RunningConfig.getContextPath() +
                    UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/domStatus";
        }

        try {
            HttpClient.asyncHttpPost(url, null, params, new AsyncCompletionHandler() {
                @Override
                public Integer onCompleted(Response response) throws Exception {
                    if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                        Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] failed to request domStatus, remote server: {}", serverIP);

                        return 1;
                    }
                    return 0;
                }
            });
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] failed to request domStatus, remote server: " + serverIP, e);
        }

    }

    @Override
    public Message get(String serverIP, String key) {
        if(serverIP == null) {
            return null;
        }

        Map<String,String> params = new HashMap<>(10);

        params.put("dom", key);

        String result;
        try {
            Loggers.SRV_LOG.info("[STATUS-SYNCHRONIZE] sync dom status from: {}, dom: {}", serverIP, key);
            result = NamingProxy.reqAPI("ip4Dom2", params, serverIP, false);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] Failed to get domain status from " + serverIP, e);
            return null;
        }

        if(result == null || result.equals(StringUtils.EMPTY)) {
            return null;
        }

        Message msg = new Message();
        msg.setData(result);

        return msg;
    }
}
