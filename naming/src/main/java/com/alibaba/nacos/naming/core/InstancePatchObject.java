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

package com.alibaba.nacos.naming.core;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Patch object for instance update. To save which variables will be update by {@link
 * com.alibaba.nacos.naming.controllers.InstanceController#patch(HttpServletRequest)} API
 *
 * @author xiweng.yy
 */
public class InstancePatchObject {
    
    private final String cluster;
    
    private final String ip;
    
    private final int port;
    
    private Map<String, String> metadata;
    
    private Double weight;
    
    private Boolean healthy;
    
    private Boolean enabled;
    
    public InstancePatchObject(String cluster, String ip, int port) {
        this.cluster = cluster;
        this.ip = ip;
        this.port = port;
    }
    
    /**
     * Will be deprecated in 2.x.
     */
    private String app;
    
    public String getCluster() {
        return cluster;
    }
    
    public String getIp() {
        return ip;
    }
    
    public int getPort() {
        return port;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public Double getWeight() {
        return weight;
    }
    
    public void setWeight(Double weight) {
        this.weight = weight;
    }
    
    public Boolean getHealthy() {
        return healthy;
    }
    
    public void setHealthy(Boolean healthy) {
        this.healthy = healthy;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getApp() {
        return app;
    }
    
    public void setApp(String app) {
        this.app = app;
    }
}
