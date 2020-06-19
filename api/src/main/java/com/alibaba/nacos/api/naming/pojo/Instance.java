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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.api.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.NUMBER_PATTERN;

/**
 * Instance.
 *
 * @author nkorange
 */
@JsonInclude(Include.NON_NULL)
public class Instance {
    
    /**
     * unique id of this instance.
     */
    private String instanceId;
    
    /**
     * instance ip
     */
    private String ip;
    
    /**
     * instance port
     */
    private int port;
    
    /**
     * instance weight
     */
    private double weight = 1.0D;
    
    /**
     * instance health status
     */
    private boolean healthy = true;
    
    /**
     * If instance is enabled to accept request
     */
    private boolean enabled = true;
    
    /**
     * If instance is ephemeral
     *
     * @since 1.0.0
     */
    private boolean ephemeral = true;
    
    /**
     * cluster information of instance
     */
    private String clusterName;
    
    /**
     * Service information of instance
     */
    private String serviceName;
    
    /**
     * user extended attributes
     */
    private Map<String, String> metadata = new HashMap<String, String>();
    
    public String getInstanceId() {
        return this.instanceId;
    }
    
    public void setInstanceId(final String instanceId) {
        this.instanceId = instanceId;
    }
    
    public String getIp() {
        return this.ip;
    }
    
    public void setIp(final String ip) {
        this.ip = ip;
    }
    
    public int getPort() {
        return this.port;
    }
    
    public void setPort(final int port) {
        this.port = port;
    }
    
    public double getWeight() {
        return this.weight;
    }
    
    public void setWeight(final double weight) {
        this.weight = weight;
    }
    
    public boolean isHealthy() {
        return this.healthy;
    }
    
    public void setHealthy(final boolean healthy) {
        this.healthy = healthy;
    }
    
    public String getClusterName() {
        return this.clusterName;
    }
    
    public void setClusterName(final String clusterName) {
        this.clusterName = clusterName;
    }
    
    public String getServiceName() {
        return this.serviceName;
    }
    
    public void setServiceName(final String serviceName) {
        this.serviceName = serviceName;
    }
    
    public Map<String, String> getMetadata() {
        return this.metadata;
    }
    
    public void setMetadata(final Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    /**
     * add meta data.
     *
     * @param key   meta data key
     * @param value meta data value
     */
    public void addMetadata(final String key, final String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<String, String>(4);
        }
        this.metadata.put(key, value);
    }
    
    public boolean isEnabled() {
        return this.enabled;
    }
    
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEphemeral() {
        return this.ephemeral;
    }
    
    public void setEphemeral(final boolean ephemeral) {
        this.ephemeral = ephemeral;
    }
    
    @Override
    public String toString() {
        return "Instance{" + "instanceId='" + this.instanceId + '\'' + ", ip='" + this.ip + '\'' + ", port=" + this.port
                + ", weight=" + this.weight + ", healthy=" + this.healthy + ", enabled=" + this.enabled + ", ephemeral="
                + this.ephemeral + ", clusterName='" + this.clusterName + '\'' + ", serviceName='" + this.serviceName
                + '\'' + ", metadata=" + this.metadata + '}';
    }
    
    public String toInetAddr() {
        return this.ip + ":" + this.port;
    }
    
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Instance)) {
            return false;
        }
        
        final Instance host = (Instance) obj;
        return Instance.strEquals(host.toString(), this.toString());
    }
    
    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }
    
    private static boolean strEquals(final String str1, final String str2) {
        return str1 == null ? str2 == null : str1.equals(str2);
    }
    
    public long getInstanceHeartBeatInterval() {
        return this.getMetaDataByKeyWithDefault(PreservedMetadataKeys.HEART_BEAT_INTERVAL,
                Constants.DEFAULT_HEART_BEAT_INTERVAL);
    }
    
    public long getInstanceHeartBeatTimeOut() {
        return this.getMetaDataByKeyWithDefault(PreservedMetadataKeys.HEART_BEAT_TIMEOUT,
                Constants.DEFAULT_HEART_BEAT_TIMEOUT);
    }
    
    public long getIpDeleteTimeout() {
        return this.getMetaDataByKeyWithDefault(PreservedMetadataKeys.IP_DELETE_TIMEOUT,
                Constants.DEFAULT_IP_DELETE_TIMEOUT);
    }
    
    public String getInstanceIdGenerator() {
        return this.getMetaDataByKeyWithDefault(PreservedMetadataKeys.INSTANCE_ID_GENERATOR,
                Constants.DEFAULT_INSTANCE_ID_GENERATOR);
    }
    
    private long getMetaDataByKeyWithDefault(final String key, final long defaultValue) {
        if (this.getMetadata() == null || this.getMetadata().isEmpty()) {
            return defaultValue;
        }
        final String value = this.getMetadata().get(key);
        if (!StringUtils.isEmpty(value) && value.matches(NUMBER_PATTERN)) {
            return Long.parseLong(value);
        }
        return defaultValue;
    }
    
    private String getMetaDataByKeyWithDefault(final String key, final String defaultValue) {
        if (this.getMetadata() == null || this.getMetadata().isEmpty()) {
            return defaultValue;
        }
        return this.getMetadata().get(key);
    }
    
}
