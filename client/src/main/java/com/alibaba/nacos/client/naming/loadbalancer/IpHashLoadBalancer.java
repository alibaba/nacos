package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.loadbalancer.LoadBalancer;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Ip-hash Load-Balancer Implementation
 * same ip will choose same index, It will be some problems when instances changed.
 * @author XCXCXCXCX
 */
public class IpHashLoadBalancer implements LoadBalancer {

    @Override
    public Instance choose(ServiceInfo serviceInfo) {
        return doChoose(serviceInfo.getHosts());
    }

    public Instance doChoose(List<Instance> instances) {
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

    /**
     * callback event
     *
     * @param event
     */
    @Override
    public void onEvent(Event event) {
        //do nothing
    }
}
