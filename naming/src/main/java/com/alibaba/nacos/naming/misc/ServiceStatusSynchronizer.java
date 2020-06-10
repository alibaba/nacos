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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;
import org.apache.commons.lang3.StringUtils;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nacos
 */
public class ServiceStatusSynchronizer implements Synchronizer {
    @Override
    public void send(final String serverIP, Message msg) {
        if(serverIP == null) {
            return;
        }

        Map<String,String> params = new HashMap<String, String>(10);

        params.put("statuses", msg.getData());
        params.put("clientIP", NetUtils.localServer());


        String url = "http://" + serverIP + ":" + ApplicationUtils.getPort() + ApplicationUtils.getContextPath() +
                UtilsAndCommons.NACOS_NAMING_CONTEXT + "/service/status";

        if (serverIP.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
            url = "http://" + serverIP + ApplicationUtils.getContextPath() +
                    UtilsAndCommons.NACOS_NAMING_CONTEXT + "/service/status";
        }

        try {
            HttpClient.asyncHttpPostLarge(url, null, JacksonUtils.toJson(params), new AsyncCompletionHandler() {
                @Override
                public Integer onCompleted(Response response) throws Exception {
                    if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                        Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] failed to request serviceStatus, remote server: {}", serverIP);

                        return 1;
                    }
                    return 0;
                }
            });
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] failed to request serviceStatus, remote server: " + serverIP, e);
        }

    }

    @Override
    public Message get(String serverIP, String key) {
        if(serverIP == null) {
            return null;
        }

        Map<String,String> params = new HashMap<>(1);

        params.put("key", key);

        String result;
        try {
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("[STATUS-SYNCHRONIZE] sync service status from: {}, service: {}", serverIP, key);
            }
            result = NamingProxy.reqAPI(ApplicationUtils.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/" + "statuses", params, serverIP);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] Failed to get service status from " + serverIP, e);
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
