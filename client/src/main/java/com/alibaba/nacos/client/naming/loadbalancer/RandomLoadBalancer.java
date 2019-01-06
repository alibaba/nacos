package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;

/**
 * Random-Without-Weight Load-Balancer Implementation
 * @author XCXCXCXCX
 */
public class RandomLoadBalancer extends BaseLoadBalancer{

    @Override
    public Instance choose(ServiceInfo serviceInfo) {
        return Balancer.getHostByRandom(serviceInfo);
    }

    /**
     * callback event
     * update cache when instances changed
     *
     * @param event
     */
    @Override
    public void onEvent(Event event) {
        //do nothing
    }
}
