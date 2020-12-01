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

import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serializable;
import java.util.Objects;

/**
 * Subscriber.
 *
 * @author nicholas
 */
public class Subscriber implements Serializable {
    
    private static final long serialVersionUID = -6256968317172033867L;
    
    private String addrStr;
    
    private String agent;
    
    private String app;
    
    private String ip;
    
    private int port;
    
    private String namespaceId;
    
    private String serviceName;
    
    private String cluster;
    
    public Subscriber(String addrStr, String agent, String app, String ip, String namespaceId, String serviceName,
            int port) {
        this(addrStr, agent, app, ip, namespaceId, serviceName, port, StringUtils.EMPTY);
    }
    
    public Subscriber(String addrStr, String agent, String app, String ip, String namespaceId, String serviceName,
            int port, String clusters) {
        this.addrStr = addrStr;
        this.agent = agent;
        this.app = app;
        this.ip = ip;
        this.port = port;
        this.namespaceId = namespaceId;
        this.serviceName = serviceName;
        this.cluster = clusters;
    }
    
    public String getAddrStr() {
        return addrStr;
    }
    
    public void setAddrStr(String addrStr) {
        this.addrStr = addrStr;
    }
    
    public String getAgent() {
        return agent;
    }
    
    public void setAgent(String agent) {
        this.agent = agent;
    }
    
    public String getApp() {
        return app;
    }
    
    public void setApp(String app) {
        this.app = app;
    }
    
    public String getIp() {
        return ip;
    }
    
    public void setIp(String ip) {
        this.ip = ip;
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
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getCluster() {
        return cluster;
    }
    
    public void setCluster(String cluster) {
        this.cluster = cluster;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Subscriber that = (Subscriber) o;
        return Objects.equals(addrStr, that.addrStr) && Objects.equals(agent, that.agent) && Objects
                .equals(app, that.app) && Objects.equals(ip, that.ip) && Objects.equals(namespaceId, that.namespaceId)
                && Objects.equals(serviceName, that.serviceName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(addrStr, agent, app, ip, namespaceId, serviceName);
    }
    
    @Override
    public String toString() {
        return "Subscriber{" + "addrStr='" + addrStr + '\'' + ", agent='" + agent + '\'' + ", app='" + app + '\''
                + ", ip='" + ip + '\'' + ", namespaceId='" + namespaceId + '\'' + ", serviceName='" + serviceName + '\''
                + '}';
    }
}
