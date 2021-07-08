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

package com.alibaba.nacos.api.naming.pojo.builder;

import com.alibaba.nacos.api.naming.pojo.Instance;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builder for {@link Instance}.
 *
 * @author xiweng.yy
 */
public class InstanceBuilder {
    
    private String instanceId;
    
    private String ip;
    
    private Integer port;
    
    private Double weight;
    
    private Boolean healthy;
    
    private Boolean enabled;
    
    private Boolean ephemeral;
    
    private String clusterName;
    
    private String serviceName;
    
    private Map<String, String> metadata = new HashMap<>();
    
    private InstanceBuilder() {
    }
    
    public InstanceBuilder setInstanceId(String instanceId) {
        this.instanceId = instanceId;
        return this;
    }
    
    public InstanceBuilder setIp(String ip) {
        this.ip = ip;
        return this;
    }
    
    public InstanceBuilder setPort(Integer port) {
        this.port = port;
        return this;
    }
    
    public InstanceBuilder setWeight(Double weight) {
        this.weight = weight;
        return this;
    }
    
    public InstanceBuilder setHealthy(Boolean healthy) {
        this.healthy = healthy;
        return this;
    }
    
    public InstanceBuilder setEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }
    
    public InstanceBuilder setEphemeral(Boolean ephemeral) {
        this.ephemeral = ephemeral;
        return this;
    }
    
    public InstanceBuilder setClusterName(String clusterName) {
        this.clusterName = clusterName;
        return this;
    }
    
    public InstanceBuilder setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }
    
    public InstanceBuilder setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    public InstanceBuilder addMetadata(String metaKey, String metaValue) {
        this.metadata.put(metaKey, metaValue);
        return this;
    }
    
    /**
     * Build a new {@link Instance}.
     *
     * @return new instance
     */
    public Instance build() {
        Instance result = new Instance();
        if (!Objects.isNull(instanceId)) {
            result.setInstanceId(instanceId);
        }
        if (!Objects.isNull(ip)) {
            result.setIp(ip);
        }
        if (!Objects.isNull(port)) {
            result.setPort(port);
        }
        if (!Objects.isNull(weight)) {
            result.setWeight(weight);
        }
        if (!Objects.isNull(healthy)) {
            result.setHealthy(healthy);
        }
        if (!Objects.isNull(enabled)) {
            result.setEnabled(enabled);
        }
        if (!Objects.isNull(ephemeral)) {
            result.setEphemeral(ephemeral);
        }
        if (!Objects.isNull(clusterName)) {
            result.setClusterName(clusterName);
        }
        if (!Objects.isNull(serviceName)) {
            result.setServiceName(serviceName);
        }
        result.setMetadata(metadata);
        return result;
    }
    
    public static InstanceBuilder newBuilder() {
        return new InstanceBuilder();
    }
}
