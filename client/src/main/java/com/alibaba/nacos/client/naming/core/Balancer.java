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
package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.utils.Chooser;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.naming.utils.LogUtils;
import com.alibaba.nacos.client.naming.utils.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author xuanyin
 */
public class Balancer {

    /**
     * report status to server
     */
    public final static List<String> UNCONSISTENT_SERVICE_WITH_ADDRESS_SERVER = new CopyOnWriteArrayList<String>();

    public static class RandomByWeight {

        public static List<Instance> selectAll(ServiceInfo serviceInfo) {
            List<Instance> hosts = nothing(serviceInfo);

            if (CollectionUtils.isEmpty(hosts)) {
                throw new IllegalStateException("no host to srv for serviceInfo: " + serviceInfo.getName());
            }

            return hosts;
        }

        public static Instance selectHost(ServiceInfo dom) {

            List<Instance> hosts = selectAll(dom);

            if (CollectionUtils.isEmpty(hosts)) {
                throw new IllegalStateException("no host to srv for service: " + dom.getName());
            }

            return getHostByRandomWeight(hosts);
        }

        public static List<Instance> nothing(ServiceInfo serviceInfo) {
            return serviceInfo.getHosts();
        }
    }

    /**
     * Return one host from the host list by random-weight.
     *
     * @param hosts The list of the host.
     * @return The random-weight result of the host
     */
    protected static Instance getHostByRandomWeight(List<Instance> hosts) {
        LogUtils.LOG.debug("entry randomWithWeight");
        if (hosts == null || hosts.size() == 0) {
            LogUtils.LOG.debug("hosts == null || hosts.size() == 0");
            return null;
        }

        Chooser<String, Instance> vipChooser = new Chooser<String, Instance>("www.taobao.com");

        LogUtils.LOG.debug("new Chooser");

        List<Pair<Instance>> hostsWithWeight = new ArrayList<Pair<Instance>>();
        for (Instance host : hosts) {
            if (host.isHealthy()) {
                hostsWithWeight.add(new Pair<Instance>(host, host.getWeight()));
            }
        }
        LogUtils.LOG.debug("for (Host host : hosts)");
        vipChooser.refresh(hostsWithWeight);
        LogUtils.LOG.debug("vipChooser.refresh");
        Instance host = vipChooser.randomWithWeight();
        return host;
    }
}
