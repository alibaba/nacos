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

package com.alibaba.nacos.api.naming.pojo;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Cluster.
 *
 * <p>The class will be serialized to json, and there are some variables and method can't use Camel naming rule for
 * compatibility
 *
 * @author nkorange
 */
@SuppressWarnings("checkstyle:abbreviationaswordinname")
public class Cluster implements Serializable {
    
    private static final long serialVersionUID = -7196138840047197271L;
    
    /**
     * Name of belonging service.
     */
    private String serviceName;
    
    /**
     * Name of cluster.
     */
    private String name;
    
    /**
     * Health check config of this cluster.
     */
    private AbstractHealthChecker healthChecker = new Tcp();
    
    /**
     * Default registered port for instances in this cluster.
     */
    private int defaultPort = 80;
    
    /**
     * Default health check port of instances in this cluster.
     */
    private int defaultCheckPort = 80;
    
    /**
     * Whether or not use instance port to do health check.
     */
    private boolean useIPPort4Check = true;
    
    private Map<String, String> metadata = new HashMap<String, String>();
    
    public Cluster() {
    
    }
    
    public Cluster(String clusterName) {
        this.name = clusterName;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public AbstractHealthChecker getHealthChecker() {
        return healthChecker;
    }
    
    public void setHealthChecker(AbstractHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }
    
    public int getDefaultPort() {
        return defaultPort;
    }
    
    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }
    
    public int getDefaultCheckPort() {
        return defaultCheckPort;
    }
    
    public void setDefaultCheckPort(int defaultCheckPort) {
        this.defaultCheckPort = defaultCheckPort;
    }
    
    public boolean isUseIPPort4Check() {
        return useIPPort4Check;
    }
    
    public void setUseIPPort4Check(boolean useIPPort4Check) {
        this.useIPPort4Check = useIPPort4Check;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
