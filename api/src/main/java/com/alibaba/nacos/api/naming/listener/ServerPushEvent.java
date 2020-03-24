package com.alibaba.nacos.api.naming.listener;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;

public class ServerPushEvent implements Event {

    private ServiceInfo serviceInfo;

    public ServerPushEvent(ServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
    }

    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    public void setServiceInfo(ServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
    }
}
