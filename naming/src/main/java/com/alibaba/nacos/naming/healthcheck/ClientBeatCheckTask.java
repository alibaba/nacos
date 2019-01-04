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
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.PushService;

import java.net.HttpURLConnection;
import java.util.List;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class ClientBeatCheckTask implements Runnable {
    private VirtualClusterDomain domain;

    public ClientBeatCheckTask(VirtualClusterDomain domain) {
        this.domain = domain;
    }

    public String taskKey() {
        return domain.getName();
    }

    @Override
    public void run() {
        try {
            if (!domain.getEnableClientBeat() || !DistroMapper.responsible(domain.getName())) {
                return;
            }

            List<IpAddress> ipAddresses = domain.allIPs();

            for (IpAddress ipAddress : ipAddresses) {
                if (System.currentTimeMillis() - ipAddress.getLastBeat() > ClientBeatProcessor.CLIENT_BEAT_TIMEOUT) {
                    if (!ipAddress.isMarked()) {
                        if (ipAddress.isValid()) {
                            ipAddress.setValid(false);
                            Loggers.EVT_LOG.info("{" + ipAddress.getClusterName() + "} {POS} {IP-DISABLED} valid: "
                                    + ipAddress.getIp() + ":" + ipAddress.getPort() + "@" + ipAddress.getClusterName()
                                    + ", region: " + DistroMapper.LOCALHOST_SITE + ", msg: " + "client timeout after "
                                    + ClientBeatProcessor.CLIENT_BEAT_TIMEOUT + ", last beat: " + ipAddress.getLastBeat());
                            PushService.domChanged(domain.getName());
                        }
                    }
                }

                if (System.currentTimeMillis() - ipAddress.getLastBeat() > domain.getIpDeleteTimeout()) {
                    // delete ip
//                    if (domain.allIPs().size() > 1) {
                    Loggers.SRV_LOG.info("[AUTO-DELETE-IP] dom: " + domain.getName() + ", ip: " + JSON.toJSONString(ipAddress));
                    deleteIP(ipAddress);
//                    }
                }
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("Exception while processing client beat time out.", e);
        } finally {
//            HealthCheckReactor.scheduleCheck(this);
        }

    }

    private void deleteIP(IpAddress ipAddress) {
        try {
            String ipList = ipAddress.getIp() + ":" + ipAddress.getPort() + "_"
                    + ipAddress.getWeight() + "_" + ipAddress.getClusterName();
            String url = "http://127.0.0.1:" + RunningConfig.getServerPort() + RunningConfig.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/remvIP4Dom?dom="
                    + domain.getName() + "&ipList=" + ipList + "&token=" + domain.getToken();
            HttpClient.HttpResult result = HttpClient.httpGet(url, null, null);
            if (result.code != HttpURLConnection.HTTP_OK) {
                Loggers.SRV_LOG.error("IP-DEAD", "failed to delete ip automatically, ip: "
                        + ipAddress.toJSON() + ", caused " + result.content + ",resp code: " + result.code);
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.error("IP-DEAD", "failed to delete ip automatically, ip: " + ipAddress.toJSON(), e);
        }

    }
}
