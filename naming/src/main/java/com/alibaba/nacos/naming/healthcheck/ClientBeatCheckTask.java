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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.healthcheck.events.InstanceHeartbeatTimeoutEvent;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.push.PushService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;

import java.net.HttpURLConnection;
import java.util.List;


/**
 * Check and update statues of ephemeral instances, remove them if they have been expired.
 *
 * @author nkorange
 */
public class ClientBeatCheckTask implements Runnable {

    private Service service;

    public ClientBeatCheckTask(Service service) {
        this.service = service;
    }

    @JsonIgnore
    public PushService getPushService() {
        return ApplicationUtils.getBean(PushService.class);
    }

    @JsonIgnore
    public DistroMapper getDistroMapper() {
        return ApplicationUtils.getBean(DistroMapper.class);
    }

    public GlobalConfig getGlobalConfig() {
        return ApplicationUtils.getBean(GlobalConfig.class);
    }

    public SwitchDomain getSwitchDomain() {
        return ApplicationUtils.getBean(SwitchDomain.class);
    }

    public String taskKey() {
        return KeyBuilder.buildServiceMetaKey(service.getNamespaceId(), service.getName());
    }

    @Override
    public void run() {
        try {
            if (!getDistroMapper().responsible(service.getName())) {
                return;
            }

            if (!getSwitchDomain().isHealthCheckEnabled()) {
                return;
            }

            List<Instance> instances = service.allIPs(true);

            // first set health status of instances:
            for (Instance instance : instances) {
                if (System.currentTimeMillis() - instance.getLastBeat() > instance.getInstanceHeartBeatTimeOut()) {
                    if (!instance.isMarked()) {
                        if (instance.isHealthy()) {
                            instance.setHealthy(false);
                            Loggers.EVT_LOG.info("{POS} {IP-DISABLED} valid: {}:{}@{}@{}, region: {}, msg: client timeout after {}, last beat: {}",
                                instance.getIp(), instance.getPort(), instance.getClusterName(), service.getName(),
                                UtilsAndCommons.LOCALHOST_SITE, instance.getInstanceHeartBeatTimeOut(), instance.getLastBeat());
                            getPushService().serviceChanged(service);
                            ApplicationUtils.publishEvent(new InstanceHeartbeatTimeoutEvent(this, instance));
                        }
                    }
                }
            }

            if (!getGlobalConfig().isExpireInstance()) {
                return;
            }

            // then remove obsolete instances:
            for (Instance instance : instances) {

                if (instance.isMarked()) {
                    continue;
                }

                if (System.currentTimeMillis() - instance.getLastBeat() > instance.getIpDeleteTimeout()) {
                    // delete instance
                    Loggers.SRV_LOG.info("[AUTO-DELETE-IP] service: {}, ip: {}", service.getName(), JacksonUtils.toJson(instance));
                    deleteIP(instance);
                }
            }

        } catch (Exception e) {
            Loggers.SRV_LOG.warn("Exception while processing client beat time out.", e);
        }

    }


    private void deleteIP(Instance instance) {

        try {
            NamingProxy.Request request = NamingProxy.Request.newRequest();
            request.appendParam("ip", instance.getIp())
                .appendParam("port", String.valueOf(instance.getPort()))
                .appendParam("ephemeral", "true")
                .appendParam("clusterName", instance.getClusterName())
                .appendParam("serviceName", service.getName())
                .appendParam("namespaceId", service.getNamespaceId());

            String url = "http://127.0.0.1:" + ApplicationUtils.getPort() + ApplicationUtils.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance?" + request.toUrl();

            // delete instance asynchronously:
            HttpClient.asyncHttpDelete(url, null, null, new AsyncCompletionHandler() {
                @Override
                public Object onCompleted(Response response) throws Exception {
                    if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                        Loggers.SRV_LOG.error("[IP-DEAD] failed to delete ip automatically, ip: {}, caused {}, resp code: {}",
                            instance.toJSON(), response.getResponseBody(), response.getStatusCode());
                    }
                    return null;
                }
            });

        } catch (Exception e) {
            Loggers.SRV_LOG.error("[IP-DEAD] failed to delete ip automatically, ip: {}, error: {}", instance.toJSON(), e);
        }
    }
}
