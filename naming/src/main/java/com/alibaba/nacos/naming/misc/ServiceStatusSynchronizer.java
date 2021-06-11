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

import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.constants.FieldsConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Service status ynchronizer.
 *
 * @author nacos
 */
public class ServiceStatusSynchronizer implements Synchronizer {
    
    @Override
    public void send(final String serverIp, Message msg) {
        if (serverIp == null) {
            return;
        }
        
        Map<String, String> params = new HashMap<String, String>(10);
    
        params.put(FieldsConstants.STATUSES, msg.getData());
        params.put(FieldsConstants.CLIENT_IP, NetUtils.localServer());
        
        String url = "http://" + serverIp + ":" + EnvUtil.getPort() + EnvUtil.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/service/status";
        
        if (InternetAddressUtil.containsPort(serverIp)) {
            url = "http://" + serverIp + EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT
                    + "/service/status";
        }
        
        try {
            HttpClient.asyncHttpPostLarge(url, null, JacksonUtils.toJson(params), new Callback<String>() {
                @Override
                public void onReceive(RestResult<String> result) {
                    if (!result.ok()) {
                        Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] failed to request serviceStatus, remote server: {}",
                                serverIp);
        
                    }
                }
    
                @Override
                public void onError(Throwable throwable) {
                    Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] failed to request serviceStatus, remote server: " + serverIp, throwable);
                }
    
                @Override
                public void onCancel() {
        
                }
            });
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] failed to request serviceStatus, remote server: " + serverIp, e);
        }
        
    }
    
    @Override
    public Message get(String serverIp, String key) {
        if (serverIp == null) {
            return null;
        }
        
        Map<String, String> params = new HashMap<>(1);
        String keyStr = "key";
        params.put(keyStr, key);
        
        String result;
        try {
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("[STATUS-SYNCHRONIZE] sync service status from: {}, service: {}", serverIp, key);
            }
            result = NamingProxy
                    .reqApi(EnvUtil.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance/"
                            + "statuses", params, serverIp);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[STATUS-SYNCHRONIZE] Failed to get service status from " + serverIp, e);
            return null;
        }
        
        if (result == null || result.equals(StringUtils.EMPTY)) {
            return null;
        }
        
        Message msg = new Message();
        msg.setData(result);
        
        return msg;
    }
}
