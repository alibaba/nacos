package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.core.EventDispatcher;
import com.alibaba.nacos.client.naming.core.HostReactor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Ip-hash Load-Balancer Implementation
 * same ip will choose same index, It will be some problems when instances changed.
 * @author XCXCXCXCX
 */
public class IpHashLoadBalancer extends BaseLoadBalancer {

    public IpHashLoadBalancer(String serviceName, List<String> clusters, HostReactor hostReactor, EventDispatcher eventDispatcher) {
        super(serviceName, clusters, hostReactor, eventDispatcher, Boolean.TRUE);
    }

    public Instance doChoose(final ServiceInfo serviceInfo) {
        return doChoose0(serviceInfo.getHosts());
    }

    public Instance doChoose0(List<Instance> instances) {
        if(instances == null || instances.size() == 0){
            return null;
        }
        String host = null;
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        return instances.get(Math.abs(host.hashCode() % instances.size()));
    }

}
