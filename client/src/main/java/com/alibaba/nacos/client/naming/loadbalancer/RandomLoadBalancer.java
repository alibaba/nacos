package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.core.EventDispatcher;
import com.alibaba.nacos.client.naming.core.HostReactor;

import java.util.List;

/**
 * Random-Without-Weight Load-Balancer Implementation
 * @author XCXCXCXCX
 */
public class RandomLoadBalancer extends BaseLoadBalancer{

    public RandomLoadBalancer(String serviceName, List<String> clusters, HostReactor hostReactor, EventDispatcher eventDispatcher) {
        super(serviceName, clusters, hostReactor, eventDispatcher, Boolean.TRUE);
    }

    /**
     * User can use ServiceInfo to control, ServiceInfo changed when instances changed
     *
     * @param serviceInfo
     * @return Instance
     */
    @Override
    public Instance doChoose(final ServiceInfo serviceInfo) {
        return Balancer.getHostByRandom(serviceInfo);
    }
}
