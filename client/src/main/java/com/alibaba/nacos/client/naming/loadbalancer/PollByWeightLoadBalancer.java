package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.core.EventDispatcher;
import com.alibaba.nacos.client.naming.core.HostReactor;

import java.util.List;

/**
 * Poll-With-Weight Load-Balancer Implementation
 * @author XCXCXCXCX
 */
public class PollByWeightLoadBalancer extends BaseLoadBalancer{

    public PollByWeightLoadBalancer(String serviceName, List<String> clusters, HostReactor hostReactor, EventDispatcher eventDispatcher) {
        super(serviceName, clusters, hostReactor, eventDispatcher, Boolean.TRUE);
    }

    @Override
    public Instance doChoose(final ServiceInfo serviceInfo) {
        return Balancer.getHostByPollWeight(serviceInfo);
    }


}
