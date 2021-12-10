package com.alibaba.nacos.naming.pojo;


import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;

/**
 * Instance Detail Info
 *
 * @author Steafan
 * @since 2.0.3
 */
public class InstanceDetailInfo implements Serializable {

    private String service;

    private String ip;

    private int port;

    private String clusterName;

    private double weight;

    private boolean healthy;

    private String instanceId;

    private JsonNode metadata;

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
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

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
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

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }
}
