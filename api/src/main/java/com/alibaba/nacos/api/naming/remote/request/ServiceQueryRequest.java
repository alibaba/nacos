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

package com.alibaba.nacos.api.naming.remote.request;

import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;

/**
 * Nacos naming query request.
 *
 * @author xiweng.yy
 */
public class ServiceQueryRequest extends NamingCommonRequest {
    
    private String cluster;
    
    private boolean healthyOnly;
    
    private int udpPort;
    
    public ServiceQueryRequest() {
    }
    
    public ServiceQueryRequest(String namespace, String serviceName) {
        super(namespace, serviceName, null);
    }
    
    @Override
    public String getType() {
        return NamingRemoteConstants.QUERY_SERVICE;
    }
    
    public String getCluster() {
        return cluster;
    }
    
    public void setCluster(String cluster) {
        this.cluster = cluster;
    }
    
    public boolean isHealthyOnly() {
        return healthyOnly;
    }
    
    public void setHealthyOnly(boolean healthyOnly) {
        this.healthyOnly = healthyOnly;
    }
    
    public int getUdpPort() {
        return udpPort;
    }
    
    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }
}
