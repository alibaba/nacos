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

package com.alibaba.nacos.naming.core.v2.pojo;

import com.alibaba.nacos.common.utils.InternetAddressUtil;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Instance POJO of client published for Nacos v2.
 *
 * @author xiweng.yy
 */
public class InstancePublishInfo implements Serializable {
    
    private static final long serialVersionUID = -74988890439616025L;
    
    private String ip;
    
    private int port;
    
    private boolean healthy;
    
    private String cluster;
    
    private Map<String, Object> extendDatum;
    
    public InstancePublishInfo() {
    }
    
    public InstancePublishInfo(String ip, int port) {
        this.ip = ip;
        this.port = port;
        extendDatum = new HashMap<>(1);
    }
    
    public void setIp(String ip) {
        this.ip = ip;
    }
    
    public String getIp() {
        return ip;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setCluster(String cluster) {
        this.cluster = cluster;
    }
    
    public String getCluster() {
        return cluster;
    }
    
    public Map<String, Object> getExtendDatum() {
        return extendDatum;
    }
    
    public void setExtendDatum(Map<String, Object> extendDatum) {
        this.extendDatum = extendDatum;
    }
    
    public boolean isHealthy() {
        return healthy;
    }
    
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
    
    public String getMetadataId() {
        return genMetadataId(ip, port, cluster);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InstancePublishInfo)) {
            return false;
        }
        InstancePublishInfo that = (InstancePublishInfo) o;
        return port == that.port && healthy == that.healthy && Objects.equals(ip, that.ip) && Objects
                .equals(extendDatum, that.extendDatum);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(ip, port, extendDatum, healthy);
    }
    
    @Override
    public String toString() {
        return "InstancePublishInfo{" + "ip='" + ip + '\'' + ", port=" + port + ", healthy=" + healthy + '}';
    }
    
    public static String genMetadataId(String ip, int port, String cluster) {
        return ip + InternetAddressUtil.IP_PORT_SPLITER + port + InternetAddressUtil.IP_PORT_SPLITER + cluster;
    }
}
