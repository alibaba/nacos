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
package com.alibaba.nacos.naming.core;

import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;
import com.alibaba.nacos.naming.healthcheck.HealthCheckStatus;
import com.alibaba.nacos.naming.healthcheck.HealthCheckTask;
import com.alibaba.nacos.naming.misc.Loggers;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nkorange
 * @author jifengnan 2019-04-26
 */
public class Cluster extends com.alibaba.nacos.api.naming.pojo.Cluster implements Cloneable {

    private static final String CLUSTER_NAME_SYNTAX = "[0-9a-zA-Z-]+";
    /**
     * a addition for same site routing, can group multiple sites into a region, like Hangzhou, Shanghai, etc.
     */
    private String sitegroup = StringUtils.EMPTY;

    private int defCkport = 80;

    private int defIPPort = -1;

    @JSONField(serialize = false)
    private HealthCheckTask checkTask;

    @JSONField(serialize = false)
    private Set<Instance> persistentInstances = new HashSet<>();

    @JSONField(serialize = false)
    private Set<Instance> ephemeralInstances = new HashSet<>();

    @JSONField(serialize = false)
    private Service service;

    @JSONField(serialize = false)
    private volatile boolean inited = false;

    private Map<String, String> metadata = new ConcurrentHashMap<>();

    /**
     * Create a cluster.
     * <p>集群名不能为空，且只能由阿拉伯数字，英文字母和减号（-）组成(the cluster name cannot be null, and only the arabic numerals, letters and endashes are allowed)。
     *
     * @param clusterName 集群名
     * @param service     服务
     * @throws IllegalArgumentException 服务为空，或者集群名为空，或者集群名不合法(the service is null, or the cluster name is null, or the cluster name is illegal)。
     * @since 1.1.0
     * @author jifengnan 2019-04-26
     */
    public Cluster(String clusterName, Service service) {
        this.setName(clusterName);
        this.service = service;
        validate();
    }

    public int getDefIPPort() {
        // for compatibility with old entries
        return defIPPort == -1 ? defCkport : defIPPort;
    }

    public void setDefIPPort(int defIPPort) {
        if (defIPPort == 0) {
            throw new IllegalArgumentException("defIPPort can not be 0");
        }
        this.defIPPort = defIPPort;
    }

    public List<Instance> allIPs() {
        List<Instance> allInstances = new ArrayList<>();
        allInstances.addAll(persistentInstances);
        allInstances.addAll(ephemeralInstances);
        return allInstances;
    }

    public List<Instance> allIPs(boolean ephemeral) {
        return ephemeral ? new ArrayList<>(ephemeralInstances) : new ArrayList<>(persistentInstances);
    }

    public void init() {
        if (inited) {
            return;
        }
        checkTask = new HealthCheckTask(this);
        HealthCheckReactor.scheduleCheck(checkTask);
        inited = true;
    }

    public void destroy() {
        if (checkTask != null) {
            checkTask.setCancelled(true);
        }
    }

    public HealthCheckTask getHealthCheckTask() {
        return checkTask;
    }

    public Service getService() {
        return service;
    }

    /**
     * 为当前的集群更换服务（replace the service for the current cluster）。
     * <p>不建议使用，集群所属的服务不应该允许被更改（服务内部的可变属性可以更改，但不应该将一个A服务的集群改成B服务）。
     * 如果一个集群对应的服务都变了，其实应该新建一个集群。
     * (Deprecated because the service shouldn't be replaced.
     * (the service fields can be changed, but the service A shouldn't be replaced to service B).
     * If the service of a cluster is required to replace, actually, a new cluster is required)
     *
     * @param service 服务
     */
    @Deprecated
    public void setService(Service service) {
        this.service = service;
    }

    /**
     * 该方法计划在未来被移除，服务名不应允许被更改。
     * (this method has been deprecated, the service name is not allowed to change)。
     *
     * @param serviceName 服务名
     * @since 1.1.0
     * @author jifengnan  2019-04-26
     */
    @Deprecated
    @Override
    public void setServiceName(String serviceName) {
        throw new UnsupportedOperationException("This method has been deprecated, the service name is not allowed to change.");
    }

    /**
     * Get the service name of the current cluster.
     * <p>注意，通过本方法获取的服务名并非是通过{@link #setServiceName(String)}设置的服务名，而是当前集群所属服务的名字。
     * (Note that the returned service name is not the name which set by {@link #setServiceName(String)},
     * but the name of the service to which the current cluster belongs).
     *
     * @return the service name of the current cluster.
     */
    @Override
    public String getServiceName() {
        return service.getName();
    }

    @Override
    public Cluster clone() throws CloneNotSupportedException {
        super.clone();
        Cluster cluster = new Cluster(this.getName(), service);
        cluster.setHealthChecker(getHealthChecker().clone());
        cluster.persistentInstances = new HashSet<>();
        cluster.checkTask = null;
        cluster.metadata = new HashMap<>(metadata);
        return cluster;
    }

    public void updateIPs(List<Instance> ips, boolean ephemeral) {

        Set<Instance> toUpdateInstances = ephemeral ? ephemeralInstances : persistentInstances;

        HashMap<String, Instance> oldIPMap = new HashMap<>(toUpdateInstances.size());

        for (Instance ip : toUpdateInstances) {
            oldIPMap.put(ip.getDatumKey(), ip);
        }

        List<Instance> updatedIPs = updatedIPs(ips, oldIPMap.values());
        if (updatedIPs.size() > 0) {
            for (Instance ip : updatedIPs) {
                Instance oldIP = oldIPMap.get(ip.getDatumKey());

                // do not update the ip validation status of updated ips
                // because the checker has the most precise result
                // Only when ip is not marked, don't we update the health status of IP:
                if (!ip.isMarked()) {
                    ip.setHealthy(oldIP.isHealthy());
                }

                if (ip.isHealthy() != oldIP.isHealthy()) {
                    // ip validation status updated
                    Loggers.EVT_LOG.info("{} {SYNC} IP-{} {}:{}@{}",
                        getService().getName(), (ip.isHealthy() ? "ENABLED" : "DISABLED"), ip.getIp(), ip.getPort(), getName());
                }

                if (ip.getWeight() != oldIP.getWeight()) {
                    // ip validation status updated
                    Loggers.EVT_LOG.info("{} {SYNC} {IP-UPDATED} {}->{}", getService().getName(), oldIP.toString(), ip.toString());
                }
            }
        }

        List<Instance> newIPs = subtract(ips, oldIPMap.values());
        if (newIPs.size() > 0) {
            Loggers.EVT_LOG.info("{} {SYNC} {IP-NEW} cluster: {}, new ips size: {}, content: {}",
                getService().getName(), getName(), newIPs.size(), newIPs.toString());

            for (Instance ip : newIPs) {
                HealthCheckStatus.reset(ip);
            }
        }

        List<Instance> deadIPs = subtract(oldIPMap.values(), ips);

        if (deadIPs.size() > 0) {
            Loggers.EVT_LOG.info("{} {SYNC} {IP-DEAD} cluster: {}, dead ips size: {}, content: {}",
                getService().getName(), getName(), deadIPs.size(), deadIPs.toString());

            for (Instance ip : deadIPs) {
                HealthCheckStatus.remv(ip);
            }
        }

        toUpdateInstances = new HashSet<>(ips);

        if (ephemeral) {
            ephemeralInstances = toUpdateInstances;
        } else {
            persistentInstances = toUpdateInstances;
        }
    }

    public List<Instance> updatedIPs(Collection<Instance> a, Collection<Instance> b) {

        List<Instance> intersects = (List<Instance>) CollectionUtils.intersection(a, b);
        Map<String, Instance> stringIPAddressMap = new ConcurrentHashMap<>(intersects.size());

        for (Instance instance : intersects) {
            stringIPAddressMap.put(instance.getIp() + ":" + instance.getPort(), instance);
        }

        Map<String, Integer> intersectMap = new ConcurrentHashMap<>(a.size() + b.size());
        Map<String, Instance> instanceMap = new ConcurrentHashMap<>(a.size());
        Map<String, Instance> instanceMap1 = new ConcurrentHashMap<>(a.size());

        for (Instance instance : b) {
            if (stringIPAddressMap.containsKey(instance.getIp() + ":" + instance.getPort())) {
                intersectMap.put(instance.toString(), 1);
            }
        }


        for (Instance instance : a) {
            if (stringIPAddressMap.containsKey(instance.getIp() + ":" + instance.getPort())) {

                if (intersectMap.containsKey(instance.toString())) {
                    intersectMap.put(instance.toString(), 2);
                } else {
                    intersectMap.put(instance.toString(), 1);
                }
            }

            instanceMap1.put(instance.toString(), instance);

        }

        for (Map.Entry<String, Integer> entry : intersectMap.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();

            if (value == 1) {
                if (instanceMap1.containsKey(key)) {
                    instanceMap.put(key, instanceMap1.get(key));
                }
            }
        }

        return new ArrayList<>(instanceMap.values());
    }

    public List<Instance> subtract(Collection<Instance> a, Collection<Instance> b) {
        Map<String, Instance> mapa = new HashMap<>(b.size());
        for (Instance o : b) {
            mapa.put(o.getIp() + ":" + o.getPort(), o);
        }

        List<Instance> result = new ArrayList<Instance>();

        for (Instance o : a) {
            if (!mapa.containsKey(o.getIp() + ":" + o.getPort())) {
                result.add(o);
            }
        }

        return result;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
            .append(getName())
            .append(service)
            .toHashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Cluster cluster = (Cluster) o;

        return new EqualsBuilder()
            .append(getName(), cluster.getName())
            .append(service, cluster.service)
            .isEquals();
    }

    public int getDefCkport() {
        return defCkport;
    }

    public void setDefCkport(int defCkport) {
        this.defCkport = defCkport;
    }

    public void update(Cluster cluster) {

        if (!getHealthChecker().equals(cluster.getHealthChecker())) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}:, healthChecker: {} -> {}",
                getService().getName(), getName(), getHealthChecker().toString(), cluster.getHealthChecker().toString());
            setHealthChecker(cluster.getHealthChecker());
        }

        if (defCkport != cluster.getDefCkport()) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, defCkport: {} -> {}",
                getService().getName(), getName(), defCkport, cluster.getDefCkport());
            defCkport = cluster.getDefCkport();
        }

        if (defIPPort != cluster.getDefIPPort()) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, defIPPort: {} -> {}",
                getService().getName(), getName(), defIPPort, cluster.getDefIPPort());
            defIPPort = cluster.getDefIPPort();
        }

        if (!StringUtils.equals(sitegroup, cluster.getSitegroup())) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, sitegroup: {} -> {}",
                getService().getName(), getName(), sitegroup, cluster.getSitegroup());
            sitegroup = cluster.getSitegroup();
        }

        if (isUseIPPort4Check() != cluster.isUseIPPort4Check()) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, useIPPort4Check: {} -> {}",
                getService().getName(), getName(), isUseIPPort4Check(), cluster.isUseIPPort4Check());
            setUseIPPort4Check(cluster.isUseIPPort4Check());
        }

        metadata = cluster.getMetadata();
    }

    public String getSitegroup() {
        return sitegroup;
    }

    public void setSitegroup(String sitegroup) {
        this.sitegroup = sitegroup;
    }

    public boolean contains(Instance ip) {
        return persistentInstances.contains(ip) || ephemeralInstances.contains(ip);
    }

    /**
     * 验证当前集群是否合法(validate the current cluster)。
     * <p>集群名不能为空，且只能由阿拉伯数字，英文字母和减号（-）组成(the cluster name cannot be null, and only the arabic numerals, letters and endashes are allowed)。
     *
     * @throws IllegalArgumentException 服务为空，或者集群名为空，或者集群名不合法(the service is null, or the cluster name is null, or the cluster name is illegal)。
     */
    public void validate() {
        Assert.notNull(getName(), "cluster name cannot be null");
        Assert.notNull(service, "service cannot be null");
        if (!getName().matches(CLUSTER_NAME_SYNTAX)) {
            throw new IllegalArgumentException("cluster name can only have these characters: 0-9a-zA-Z-, current: " + getName());
        }
    }
}
