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
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.api.common.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * @author dungu.zpf
 */
public class Instance {

    /**
     * Unique ID of this instance.
     */
    private String instanceId;

    /**
     * Instance ip
     */
    private String ip;

    /**
     * Instance port
     */
    private int port;

    /**
     * Instance weight
     */
    private double weight = 1.0D;

    /**
     * Instance health status
     */
    @JSONField(name = "valid")
    private boolean healthy = true;

    /**
     * Cluster information of instance
     */
    @JSONField(serialize = false)
    private Cluster cluster = new Cluster();

    /**
     * Service information of instance
     */
    @JSONField(serialize = false)
    private Service service;

    /**
     * User extended attributes
     */
    private Map<String, String> metadata = new HashMap<String, String>();

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String serviceName() {
        String[] infos = instanceId.split(Constants.NAMING_INSTANCE_ID_SPLITTER);
        if (infos.length < Constants.NAMING_INSTANCE_ID_SEG_COUNT) {
            return null;
        }
        return infos[Constants.NAMING_INSTANCE_ID_SEG_COUNT - 1];
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

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
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

}
