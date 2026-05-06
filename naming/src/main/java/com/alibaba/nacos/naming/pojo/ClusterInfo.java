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

package com.alibaba.nacos.naming.pojo;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Cluster info.
 *
 * @author caogu.wyp
 * @version $Id: ClusterInfo.java, v 0.1 2018-09-17 上午11:36 caogu.wyp Exp $$
 *
 * @deprecated use {@link com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo} replaced.
 */
@Deprecated
public class ClusterInfo implements Serializable {

    private static final long serialVersionUID = 2146881454057032105L;

    private String clusterName;
    
    private AbstractHealthChecker healthChecker;
    
    private Map<String, String> metadata;
    
    private List<IpAddressInfo> hosts;
    
    /**
     * Getter method for property <tt>hosts</tt>.
     *
     * @return property value of hosts
     */
    public List<IpAddressInfo> getHosts() {
        return hosts;
    }
    
    /**
     * Setter method for property <tt>hosts </tt>.
     *
     * @param hosts value to be assigned to property hosts
     */
    public void setHosts(List<IpAddressInfo> hosts) {
        this.hosts = hosts;
    }
    
    public String getClusterName() {
        return clusterName;
    }
    
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }
    
    public AbstractHealthChecker getHealthChecker() {
        return healthChecker;
    }
    
    public void setHealthChecker(AbstractHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    /**
     * For some old apis, from new {@link com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo} to this deprecated one.
     *
     * @param newClusterInfo new {@link com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo}
     * @return this deprecated one
     */
    public static ClusterInfo from(com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo newClusterInfo) {
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterName(newClusterInfo.getClusterName());
        clusterInfo.setHealthChecker(newClusterInfo.getHealthChecker());
        clusterInfo.setMetadata(newClusterInfo.getMetadata());
        List<IpAddressInfo> hosts = new LinkedList<>();
        for (Instance each : newClusterInfo.getHosts()) {
            IpAddressInfo ipAddressInfo = new IpAddressInfo();
            ipAddressInfo.setIp(each.getIp());
            ipAddressInfo.setPort(each.getPort());
            ipAddressInfo.setWeight(each.getWeight());
            ipAddressInfo.setMetadata(each.getMetadata());
            ipAddressInfo.setEnabled(each.isEnabled());
            ipAddressInfo.setValid(each.isHealthy());
            hosts.add(ipAddressInfo);
        }
        clusterInfo.setHosts(hosts);
        return clusterInfo;
    }
}
