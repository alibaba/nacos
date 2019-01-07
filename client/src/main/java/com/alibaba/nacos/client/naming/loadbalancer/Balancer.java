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
package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.utils.Chooser;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.naming.utils.LogUtils;
import com.alibaba.nacos.client.naming.utils.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author xuanyin
 */
public final class Balancer {

    /**
     * report status to server
     */
    public final static List<String> UNCONSISTENT_SERVICE_WITH_ADDRESS_SERVER = new CopyOnWriteArrayList<String>();

    /**
     * cache chooser
     */
    static final Map<String, Chooser<String, Instance>> pollCacheChooser =
        new ConcurrentHashMap<String, Chooser<String, Instance>>();

    /**
     * Return one host from the host list by random.
     *
     * @param serviceInfo The list of the host.
     * @return The random result of the host
     */
    protected static Instance getHostByRandom(final ServiceInfo serviceInfo) {
        List<Instance> hosts = selectAll(serviceInfo);

        LogUtils.LOG.debug("entry random");
        Chooser<String, Instance> vipChooser = new Chooser<String, Instance>("load_balance_random");
        LogUtils.LOG.debug("new Chooser");

        List<Pair<Instance>> hostsWithoutWeight = new ArrayList<Pair<Instance>>();
        for (Instance host : hosts) {
            if (host.isHealthy()) {
                hostsWithoutWeight.add(new Pair<Instance>(host, host.getWeight()));
            }
        }
        LogUtils.LOG.debug("for (Host host : hosts)");
        vipChooser.refresh(hostsWithoutWeight);
        LogUtils.LOG.debug("vipChooser.refresh");
        return vipChooser.random();
    }

    /**
     * Return one host from the host list by random-weight.
     *
     * @param serviceInfo The list of the host.
     * @return The random-weight result of the host
     */
    protected static Instance getHostByRandomWeight(final ServiceInfo serviceInfo) {
        List<Instance> hosts = selectAll(serviceInfo);

        LogUtils.LOG.debug("entry randomWithWeight");
        Chooser<String, Instance> vipChooser = new Chooser<String, Instance>("load_balance_random_with_weight");
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

        return vipChooser.randomWithWeight();
    }

    /**
     * Return one host from the host list by poll.
     *
     * @param serviceInfo The list of the host.
     * @return The poll result of the host
     */
    protected static Instance getHostByPoll(final ServiceInfo serviceInfo) {

        LogUtils.LOG.debug("entry poll");

        Chooser<String, Instance> vipChooser = pollCacheChooser.get(serviceInfo.getName());

        List<Instance> hosts = selectAll(serviceInfo);
        Chooser<String, Instance> tmpChooser = new Chooser<String, Instance>("load_balance_poll");
        LogUtils.LOG.debug("new Chooser");
        List<Pair<Instance>> hostsWithoutWeight = new ArrayList<Pair<Instance>>();
        for (Instance host : hosts) {
            if (host.isHealthy()) {
                hostsWithoutWeight.add(new Pair<Instance>(host, host.getWeight()));
            }
        }
        LogUtils.LOG.debug("for (Host host : hosts)");
        tmpChooser.refresh(hostsWithoutWeight);
        LogUtils.LOG.debug("vipChooser.refresh");

        if (vipChooser == null || !tmpChooser.getRef().equals(vipChooser.getRef())) {
            vipChooser = tmpChooser;
            pollCacheChooser.put(serviceInfo.getName(), vipChooser);
        }
        return vipChooser.poll();
    }

    /**
     * Return one host from the host list by poll-weight.
     *
     * @param serviceInfo The serviceInfo contains serviceName and clusters and hosts
     * @return The poll-weight result of the host
     */
    protected static Instance getHostByPollWeight(final ServiceInfo serviceInfo) {

        LogUtils.LOG.debug("entry pollWithWeight");

        Chooser<String, Instance> vipChooser = pollCacheChooser.get(serviceInfo.getName());

        List<Instance> hosts = selectAll(serviceInfo);
        Chooser<String, Instance> tmpChooser = new Chooser<String, Instance>("load_balance_poll_with_weight");
        LogUtils.LOG.debug("new Chooser");
        List<Pair<Instance>> hostsWithWeight = new ArrayList<Pair<Instance>>();
        for (Instance host : hosts) {
            if (host.isHealthy()) {
                hostsWithWeight.add(new Pair<Instance>(host, host.getWeight()));
            }
        }
        LogUtils.LOG.debug("for (Host host : hosts)");
        tmpChooser.refresh(hostsWithWeight);
        LogUtils.LOG.debug("vipChooser.refresh");

        if (vipChooser == null || !tmpChooser.getRef().equals(vipChooser.getRef())) {
            vipChooser = tmpChooser;
            pollCacheChooser.put(serviceInfo.getName(), vipChooser);
        }
        return vipChooser.pollWithWeight();
    }


    public static List<Instance> selectAll(ServiceInfo serviceInfo) {
        List<Instance> hosts = nothing(serviceInfo);

        if (CollectionUtils.isEmpty(hosts)) {
            throw new IllegalStateException("no host to srv for serviceInfo: " + serviceInfo.getName());
        }

        return hosts;
    }

    public static List<Instance> nothing(ServiceInfo serviceInfo) {
        return serviceInfo.getHosts();
    }
}
