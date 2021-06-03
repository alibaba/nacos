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

import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;
import com.alibaba.nacos.naming.healthcheck.HealthCheckStatus;
import com.alibaba.nacos.naming.healthcheck.HealthCheckTask;
import com.alibaba.nacos.naming.misc.Loggers;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cluster.
 *
 * @author nkorange
 * @author jifengnan 2019-04-26
 */
public class Cluster extends com.alibaba.nacos.api.naming.pojo.Cluster implements Cloneable {
    
    private static final String CLUSTER_NAME_SYNTAX = "[0-9a-zA-Z-]+";
    
    private static final long serialVersionUID = 8940123791150907510L;
    
    /**
     * a addition for same site routing, can group multiple sites into a region, like Hangzhou, Shanghai, etc.
     */
    private String sitegroup = StringUtils.EMPTY;
    
    private int defCkport = 80;
    
    private int defIpPort = -1;
    
    @JsonIgnore
    private HealthCheckTask checkTask;
    
    @JsonIgnore
    private Set<Instance> persistentInstances = new HashSet<>();
    
    @JsonIgnore
    private Set<Instance> ephemeralInstances = new HashSet<>();
    
    @JsonIgnore
    private Service service;
    
    @JsonIgnore
    private volatile boolean inited = false;
    
    private Map<String, String> metadata = new ConcurrentHashMap<>();
    
    public Cluster() {
    }
    
    /**
     * Create a cluster.
     *
     * <p>the cluster name cannot be null, and only the arabic numerals, letters and endashes are allowed.
     *
     * @param clusterName the cluster name
     * @param service     the service to which the current cluster belongs
     * @throws IllegalArgumentException the service is null, or the cluster name is null, or the cluster name is
     *                                  illegal
     * @author jifengnan 2019-04-26
     * @since 1.0.1
     */
    public Cluster(String clusterName, Service service) {
        this.setName(clusterName);
        this.service = service;
        validate();
    }
    
    /**
     * Reason why method is not camel is that the old version has released, and the method name will be as the key
     * serialize and deserialize for Json. So ignore checkstyle.
     *
     * @return default port
     */
    @SuppressWarnings("checkstyle:abbreviationaswordinname")
    public int getDefIPPort() {
        // for compatibility with old entries
        return defIpPort == -1 ? defCkport : defIpPort;
    }
    
    @SuppressWarnings("checkstyle:abbreviationaswordinname")
    public void setDefIPPort(int defIpPort) {
        if (defIpPort == 0) {
            throw new IllegalArgumentException("defIPPort can not be 0");
        }
        this.defIpPort = defIpPort;
    }
    
    /**
     * Get all instances.
     *
     * @return list of instance
     */
    public List<Instance> allIPs() {
        List<Instance> allInstances = new ArrayList<>();
        allInstances.addAll(persistentInstances);
        allInstances.addAll(ephemeralInstances);
        return allInstances;
    }
    
    /**
     * Get all ephemeral or consistence instances.
     *
     * @param ephemeral whether returned instances are ephemeral
     * @return list of special instances
     */
    public List<Instance> allIPs(boolean ephemeral) {
        return ephemeral ? new ArrayList<>(ephemeralInstances) : new ArrayList<>(persistentInstances);
    }
    
    /**
     * Init cluster.
     */
    public void init() {
        if (inited) {
            return;
        }
        checkTask = new HealthCheckTask(this);
        
        HealthCheckReactor.scheduleCheck(checkTask);
        inited = true;
    }
    
    /**
     * Destroy cluster.
     */
    public void destroy() {
        if (checkTask != null) {
            checkTask.setCancelled(true);
        }
    }
    
    @JsonIgnore
    public HealthCheckTask getHealthCheckTask() {
        return checkTask;
    }
    
    public Service getService() {
        return service;
    }
    
    /**
     * Replace the service for the current cluster.
     *
     * <p>the service shouldn't be replaced. so if the service is not empty will nothing to do.
     * (the service fields can be changed, but the service A shouldn't be replaced to service B). If the service of a
     * cluster is required to replace, actually, a new cluster is required.
     *
     * @param service the new service
     */
    public void setService(Service service) {
        if (this.service != null) {
            return;
        }
        this.service = service;
    }
    
    /**
     * this method has been deprecated, the service name shouldn't be changed.
     *
     * @param serviceName the service name
     * @author jifengnan  2019-04-26
     * @since 1.0.1
     */
    @Deprecated
    @Override
    public void setServiceName(String serviceName) {
        super.setServiceName(serviceName);
    }
    
    /**
     * Get the service name of the current cluster.
     *
     * <p>Note that the returned service name is not the name which set by {@link #setServiceName(String)},
     * but the name of the service to which the current cluster belongs.
     *
     * @return the service name of the current cluster.
     */
    @Override
    public String getServiceName() {
        if (service != null) {
            return service.getName();
        } else {
            return super.getServiceName();
        }
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
    
    public boolean isEmpty() {
        return ephemeralInstances.isEmpty() && persistentInstances.isEmpty();
    }
    
    /**
     * Update instance list.
     *
     * @param ips       instance list
     * @param ephemeral whether these instances are ephemeral
     */
    public void updateIps(List<Instance> ips, boolean ephemeral) {
        
        Set<Instance> toUpdateInstances = ephemeral ? ephemeralInstances : persistentInstances;
        
        HashMap<String, Instance> oldIpMap = new HashMap<>(toUpdateInstances.size());
        
        for (Instance ip : toUpdateInstances) {
            oldIpMap.put(ip.getDatumKey(), ip);
        }
        
        List<Instance> updatedIps = updatedIps(ips, oldIpMap.values());
        if (updatedIps.size() > 0) {
            for (Instance ip : updatedIps) {
                Instance oldIP = oldIpMap.get(ip.getDatumKey());
                
                // do not update the ip validation status of updated ips
                // because the checker has the most precise result
                // Only when ip is not marked, don't we update the health status of IP:
                if (!ip.isMarked()) {
                    ip.setHealthy(oldIP.isHealthy());
                }
                
                if (ip.isHealthy() != oldIP.isHealthy()) {
                    // ip validation status updated
                    Loggers.EVT_LOG.info("{} {SYNC} IP-{} {}:{}@{}", getService().getName(),
                            (ip.isHealthy() ? "ENABLED" : "DISABLED"), ip.getIp(), ip.getPort(), getName());
                }
                
                if (ip.getWeight() != oldIP.getWeight()) {
                    // ip validation status updated
                    Loggers.EVT_LOG.info("{} {SYNC} {IP-UPDATED} {}->{}", getService().getName(), oldIP, ip);
                }
            }
        }
        
        List<Instance> newIPs = subtract(ips, oldIpMap.values());
        if (newIPs.size() > 0) {
            Loggers.EVT_LOG
                    .info("{} {SYNC} {IP-NEW} cluster: {}, new ips size: {}, content: {}", getService().getName(),
                            getName(), newIPs.size(), newIPs);
            
            for (Instance ip : newIPs) {
                HealthCheckStatus.reset(ip);
            }
        }
        
        List<Instance> deadIPs = subtract(oldIpMap.values(), ips);
        
        if (deadIPs.size() > 0) {
            Loggers.EVT_LOG
                    .info("{} {SYNC} {IP-DEAD} cluster: {}, dead ips size: {}, content: {}", getService().getName(),
                            getName(), deadIPs.size(), deadIPs);
            
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
    
    private List<Instance> updatedIps(Collection<Instance> newInstance, Collection<Instance> oldInstance) {
        
        List<Instance> intersects = (List<Instance>) CollectionUtils.intersection(newInstance, oldInstance);
        Map<String, Instance> stringIpAddressMap = new ConcurrentHashMap<>(intersects.size());
        
        for (Instance instance : intersects) {
            stringIpAddressMap.put(instance.getIp() + ":" + instance.getPort(), instance);
        }
        
        Map<String, Integer> intersectMap = new ConcurrentHashMap<>(newInstance.size() + oldInstance.size());
        Map<String, Instance> updatedInstancesMap = new ConcurrentHashMap<>(newInstance.size());
        Map<String, Instance> newInstancesMap = new ConcurrentHashMap<>(newInstance.size());
        
        for (Instance instance : oldInstance) {
            if (stringIpAddressMap.containsKey(instance.getIp() + ":" + instance.getPort())) {
                intersectMap.put(instance.toString(), 1);
            }
        }
        
        for (Instance instance : newInstance) {
            if (stringIpAddressMap.containsKey(instance.getIp() + ":" + instance.getPort())) {
                
                if (intersectMap.containsKey(instance.toString())) {
                    intersectMap.put(instance.toString(), 2);
                } else {
                    intersectMap.put(instance.toString(), 1);
                }
            }
            
            newInstancesMap.put(instance.toString(), instance);
            
        }
        
        for (Map.Entry<String, Integer> entry : intersectMap.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            
            if (value == 1) {
                if (newInstancesMap.containsKey(key)) {
                    updatedInstancesMap.put(key, newInstancesMap.get(key));
                }
            }
        }
        
        return new ArrayList<>(updatedInstancesMap.values());
    }
    
    private List<Instance> subtract(Collection<Instance> oldIp, Collection<Instance> ips) {
        Map<String, Instance> ipsMap = new HashMap<>(ips.size());
        for (Instance instance : ips) {
            ipsMap.put(instance.getIp() + ":" + instance.getPort(), instance);
        }
        
        List<Instance> instanceResult = new ArrayList<>();
        
        for (Instance instance : oldIp) {
            if (!ipsMap.containsKey(instance.getIp() + ":" + instance.getPort())) {
                instanceResult.add(instance);
            }
        }
        return instanceResult;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Cluster)) {
            return false;
        }
        
        return getName().equals(((Cluster) obj).getName());
    }
    
    public int getDefCkport() {
        return defCkport;
    }
    
    public void setDefCkport(int defCkport) {
        this.defCkport = defCkport;
        super.setDefaultCheckPort(defCkport);
    }
    
    /**
     * Update cluster from other cluster.
     *
     * @param cluster new cluster
     */
    public void update(Cluster cluster) {
        
        if (!getHealthChecker().equals(cluster.getHealthChecker())) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}:, healthChecker: {} -> {}", getService().getName(), getName(),
                    getHealthChecker().toString(), cluster.getHealthChecker().toString());
            setHealthChecker(cluster.getHealthChecker());
        }
        
        if (defCkport != cluster.getDefCkport()) {
            Loggers.SRV_LOG
                    .info("[CLUSTER-UPDATE] {}:{}, defCkport: {} -> {}", getService().getName(), getName(), defCkport,
                            cluster.getDefCkport());
            defCkport = cluster.getDefCkport();
        }
        
        if (defIpPort != cluster.getDefIPPort()) {
            Loggers.SRV_LOG
                    .info("[CLUSTER-UPDATE] {}:{}, defIPPort: {} -> {}", getService().getName(), getName(), defIpPort,
                            cluster.getDefIPPort());
            defIpPort = cluster.getDefIPPort();
        }
        
        if (!StringUtils.equals(sitegroup, cluster.getSitegroup())) {
            Loggers.SRV_LOG
                    .info("[CLUSTER-UPDATE] {}:{}, sitegroup: {} -> {}", getService().getName(), getName(), sitegroup,
                            cluster.getSitegroup());
            sitegroup = cluster.getSitegroup();
        }
        
        if (isUseIPPort4Check() != cluster.isUseIPPort4Check()) {
            Loggers.SRV_LOG.info("[CLUSTER-UPDATE] {}:{}, useIPPort4Check: {} -> {}", getService().getName(), getName(),
                    isUseIPPort4Check(), cluster.isUseIPPort4Check());
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
     * validate the current cluster.
     *
     * <p>the cluster name cannot be null, and only the arabic numerals, letters and endashes are allowed.
     *
     * @throws IllegalArgumentException the service is null, or the cluster name is null, or the cluster name is
     *                                  illegal
     */
    public void validate() {
        Assert.notNull(getName(), "cluster name cannot be null");
        Assert.notNull(service, "service cannot be null");
        if (!getName().matches(CLUSTER_NAME_SYNTAX)) {
            throw new IllegalArgumentException(
                    "cluster name can only have these characters: 0-9a-zA-Z-, current: " + getName());
        }
    }
}
