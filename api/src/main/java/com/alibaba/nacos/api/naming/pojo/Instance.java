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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.NUMBER_PATTERN;

/**
 * Instance
 *
 * @author nkorange
 */
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
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public void addMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<String, String>(4);
        }
        this.metadata.put(key, value);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    public String toInetAddr() {
        return ip + ":" + port;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Instance)) {
            return false;
        }

        Instance host = (Instance) obj;

        return strEquals(toString(), host.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    private static boolean strEquals(String str1, String str2) {
        return str1 == null ? str2 == null : str1.equals(str2);
    }

    public long getInstanceHeartBeatInterval() {
        return getMetaDataByKeyWithDefault(PreservedMetadataKeys.HEART_BEAT_INTERVAL, Constants.DEFAULT_HEART_BEAT_INTERVAL);
    }

    public long getInstanceHeartBeatTimeOut() {
        return getMetaDataByKeyWithDefault(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, Constants.DEFAULT_HEART_BEAT_TIMEOUT);
    }

    public long getIpDeleteTimeout() {
        return getMetaDataByKeyWithDefault(PreservedMetadataKeys.IP_DELETE_TIMEOUT, Constants.DEFAULT_IP_DELETE_TIMEOUT);
    }

    public String getInstanceIdGenerator() {
        return getMetaDataByKeyWithDefault(PreservedMetadataKeys.INSTANCE_ID_GENERATOR, Constants.DEFAULT_INSTANCE_ID_GENERATOR);
    }

    private long getMetaDataByKeyWithDefault( String key, long defaultValue) {
        if (getMetadata() == null || getMetadata().isEmpty()) {
            return defaultValue;
        }
        String value = getMetadata().get(key);
        if (!StringUtils.isEmpty(value) && value.matches(NUMBER_PATTERN)) {
            return Long.parseLong(value);
        }
        return defaultValue;
    }

    private String getMetaDataByKeyWithDefault( String key, String defaultValue) {
        if (getMetadata() == null || getMetadata().isEmpty()) {
            return defaultValue;
        }
        return getMetadata().get(key);
    }

}
