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

import com.alibaba.nacos.common.utils.IPUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.model.RestResult;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Report local server status to other server.
 *
 * @author nacos
 * @deprecated 1.3.0 This object will be deleted sometime after version 1.3.0
 */
public class ServerStatusSynchronizer implements Synchronizer {
    
    @Override
    public void send(final String serverIP, Message msg) {
        if (StringUtils.isEmpty(serverIP)) {
            return;
        }
        
        final Map<String, String> params = new HashMap<String, String>(2);
        
        params.put("serverStatus", msg.getData());
        
        String url = "http://" + serverIP + ":" + EnvUtil.getPort() + EnvUtil.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/operator/server/status";
        
        if (IPUtil.containsPort(serverIP)) {
            url = "http://" + serverIP + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT
                    + "/operator/server/status";
        }
        
        try {
            HttpClient.asyncHttpGet(url, null, params, new Callback<String>() {
                @Override
                public void onReceive(RestResult<String> result) {
                    if (!result.ok()) {
                        Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] failed to request serverStatus, remote server: {}",
                                serverIP);
                    }
                }
    
                @Override
                public void onError(Throwable throwable) {
                    Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] failed to request serverStatus, remote server: {}", serverIP, throwable);
                }
    
                @Override
                public void onCancel() {
        
                }
            });
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] failed to request serverStatus, remote server: {}", serverIP, e);
        }
    }
    
    @Override
    public Message get(String server, String key) {
        return null;
    }
}
