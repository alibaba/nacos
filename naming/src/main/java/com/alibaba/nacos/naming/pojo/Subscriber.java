package com.alibaba.nacos.naming.pojo;

import java.io.Serializable;

public class Subscriber implements Serializable {

    private String addrStr;

    private String agent;

    private String app;

    private String ip;

    private String namespaceId;

    private String serviceName;

    public Subscriber(String addrStr, String agent, String app, String ip, String namespaceId, String serviceName) {
        this.addrStr = addrStr;
        this.agent = agent;
        this.app = app;
        this.ip = ip;
        this.namespaceId = namespaceId;
        this.serviceName = serviceName;
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
}
