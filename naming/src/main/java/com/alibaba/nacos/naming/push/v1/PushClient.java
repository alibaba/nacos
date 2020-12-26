/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v1;

import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;

/**
 * Push Client for v1.x.
 *
 * @author xiweng.yy
 */
public class PushClient {
    
    private String namespaceId;
    
    private String serviceName;
    
    private String clusters;
    
    private String agent;
    
    private String tenant;
    
    private String app;
    
    private InetSocketAddress socketAddr;
    
    private DataSource dataSource;
    
    private Map<String, String[]> params;
    
    public Map<String, String[]> getParams() {
        return params;
    }
    
    public void setParams(Map<String, String[]> params) {
        this.params = params;
    }
    
    public long lastRefTime = System.currentTimeMillis();
    
    public PushClient(String namespaceId, String serviceName, String clusters, String agent,
            InetSocketAddress socketAddr, DataSource dataSource, String tenant, String app) {
        this.namespaceId = namespaceId;
        this.serviceName = serviceName;
        this.clusters = clusters;
        this.agent = agent;
        this.socketAddr = socketAddr;
        this.dataSource = dataSource;
        this.tenant = tenant;
        this.app = app;
    }
    
    public DataSource getDataSource() {
        return dataSource;
    }
    
    public boolean zombie() {
        return System.currentTimeMillis() - lastRefTime > ApplicationUtils.getBean(SwitchDomain.class)
                .getPushCacheMillis(serviceName);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("serviceName: ").append(serviceName).append(", clusters: ").append(clusters).append(", address: ")
                .append(socketAddr).append(", agent: ").append(agent);
        return sb.toString();
    }
    
    public String getAgent() {
        return agent;
    }
    
    public String getAddrStr() {
        return socketAddr.getAddress().getHostAddress() + ":" + socketAddr.getPort();
    }
    
    public String getIp() {
        return socketAddr.getAddress().getHostAddress();
    }
    
    public int getPort() {
        return socketAddr.getPort();
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(serviceName, clusters, socketAddr);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PushClient)) {
            return false;
        }
        
        PushClient other = (PushClient) obj;
        
        return serviceName.equals(other.serviceName) && clusters.equals(other.clusters) && socketAddr
                .equals(other.socketAddr);
    }
    
    public String getClusters() {
        return clusters;
    }
    
    public void setClusters(String clusters) {
        this.clusters = clusters;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    public String getApp() {
        return app;
    }
    
    public void setApp(String app) {
        this.app = app;
    }
    
    public InetSocketAddress getSocketAddr() {
        return socketAddr;
    }
    
    public void refresh() {
        lastRefTime = System.currentTimeMillis();
    }
    
}
