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
package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.boot.SpringContext;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingProxy;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.PushService;

import java.net.HttpURLConnection;
import java.util.List;

/**
 * @author nkorange
 */
public class ClientBeatCheckTask implements Runnable {

    private Service service;

    public ClientBeatCheckTask(Service service) {
        this.service = service;
    }


    @JSONField(serialize = false)
    public PushService getPushService() {
        return SpringContext.getAppContext().getBean(PushService.class);
    }

    @JSONField(serialize = false)
    public DistroMapper getDistroMapper() {
        return SpringContext.getAppContext().getBean(DistroMapper.class);
    }

    public String taskKey() {
        return service.getName();
    }

    @Override
    public void run() {
        try {
            if (!getDistroMapper().responsible(service.getName())) {
                return;
            }

            List<Instance> instances = service.allIPs(true);

            for (Instance instance : instances) {
                if (System.currentTimeMillis() - instance.getLastBeat() > ClientBeatProcessor.CLIENT_BEAT_TIMEOUT) {
                    if (!instance.isMarked()) {
                        if (instance.isValid()) {
                            instance.setValid(false);
                            Loggers.EVT_LOG.info("{POS} {IP-DISABLED} valid: {}:{}@{}, region: {}, msg: client timeout after {}, last beat: {}",
                                instance.getIp(), instance.getPort(), instance.getClusterName(),
                                UtilsAndCommons.LOCALHOST_SITE, ClientBeatProcessor.CLIENT_BEAT_TIMEOUT, instance.getLastBeat());
                            getPushService().serviceChanged(service.getNamespaceId(), service.getName());
                        }
                    }
                }

                if (System.currentTimeMillis() - instance.getLastBeat() > service.getIpDeleteTimeout()) {

                    // protect threshold met:
                    if (service.meetProtectThreshold()) {
                        Loggers.SRV_LOG.info("protect threshold met, service: {}, ip: {}, healthy: {}, total: {}",
                            service.getName(), JSON.toJSONString(instance), service.healthyInstanceCount(), service.allIPs().size());
                        return;
                    }

                    // delete instance
                    Loggers.SRV_LOG.info("[AUTO-DELETE-IP] service: {}, ip: {}", service.getName(), JSON.toJSONString(instance));
                    deleteIP(instance);
                }
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("Exception while processing client beat time out.", e);
        }

    }

    private void deleteIP(Instance instance) {
        // TODO async delete
        try {
            NamingProxy.Request request = NamingProxy.Request.newRequest();
            request.appendParam("ip", instance.getIp())
                .appendParam("port", String.valueOf(instance.getPort()))
                .appendParam("clusterName", instance.getClusterName())
                .appendParam("serviceName", service.getName())
                .appendParam("namespaceId", service.getNamespaceId());

            String url = "http://127.0.0.1:" + RunningConfig.getServerPort() + RunningConfig.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance?" + request.toUrl();
            HttpClient.HttpResult result = HttpClient.httpDelete(url, null, null);
            if (result.code != HttpURLConnection.HTTP_OK) {
                Loggers.SRV_LOG.error("[IP-DEAD] failed to delete ip automatically, ip: {}, caused {}, resp code: {}",
                    instance.toJSON(), result.content, result.code);
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.error("[IP-DEAD] failed to delete ip automatically, ip: {}, error: {}", instance.toJSON(), e);
        }
    }
}
