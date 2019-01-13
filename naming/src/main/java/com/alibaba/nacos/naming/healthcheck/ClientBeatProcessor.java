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


import com.alibaba.nacos.naming.core.*;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.PushService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class ClientBeatProcessor implements Runnable {
    public static final long CLIENT_BEAT_TIMEOUT = TimeUnit.SECONDS.toMillis(15);
    private RsInfo rsInfo;
    private Domain domain;

    public RsInfo getRsInfo() {
        return rsInfo;
    }

    public void setRsInfo(RsInfo rsInfo) {
        this.rsInfo = rsInfo;
    }

    public Domain getDomain() {
        return domain;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    public ClientBeatProcessor() {

    }

    public String getType() {
        return "CLIENT_BEAT";
    }

    public void process() {
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domain;
        if (!virtualClusterDomain.getEnableClientBeat()) {
            return;
        }

        Loggers.EVT_LOG.debug("[CLIENT-BEAT] processing beat: {}", rsInfo.toString());

        String ip = rsInfo.getIp();
        String clusterName = rsInfo.getCluster();
        int port = rsInfo.getPort();
        Cluster cluster = virtualClusterDomain.getClusterMap().get(clusterName);
        List<IpAddress> ipAddresses = cluster.allIPs();

        for (IpAddress ipAddress: ipAddresses) {
            if (ipAddress.getIp().equals(ip) && ipAddress.getPort() == port) {
                Loggers.EVT_LOG.debug("[CLIENT-BEAT] refresh beat: {}", rsInfo.toString());
                ipAddress.setLastBeat(System.currentTimeMillis());
                if (!ipAddress.isMarked()) {
                    if (!ipAddress.isValid()) {
                        ipAddress.setValid(true);
                        Loggers.EVT_LOG.info("dom: {} {POS} {IP-ENABLED} valid: {}:{}@{}, region: {}, msg: client beat ok",
                            cluster.getDom().getName(), ip, port, cluster.getName(), DistroMapper.LOCALHOST_SITE);
                        PushService.domChanged(virtualClusterDomain.getNamespaceId(), domain.getName());
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        process();
    }
}
