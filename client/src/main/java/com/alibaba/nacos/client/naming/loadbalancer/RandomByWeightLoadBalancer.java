package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;

/**
 * Random-With-Weight Load-Balancer Implementation
 * @author XCXCXCXCX
 */
public class RandomByWeightLoadBalancer extends BaseLoadBalancer{

    @Override
    public Instance choose(ServiceInfo serviceInfo) {
        return Balancer.getHostByRandomWeight(serviceInfo);
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
