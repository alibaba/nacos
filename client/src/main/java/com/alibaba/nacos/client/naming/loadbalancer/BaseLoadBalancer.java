package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.loadbalancer.LoadBalancer;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;

/**
 * Base Load-balancer Abstract Class
 * User defines their own load-balancer
 * by inheriting base {@link BaseLoadBalancer}
 * or implementing LoadBalancer{@link LoadBalancer} interface
 *
 * When implementing your own Load-balancer,
 * you need to understand the two classes {@link ServiceInfo} and {@link Instance}.
 *
 * @author XCXCXCXCX
 */
public abstract class BaseLoadBalancer implements LoadBalancer{

    public abstract Instance choose(ServiceInfo serviceInfo);

    /**
     * callback event
     * update cache when instances changed
     * @param event
     */
    @Override
    public void onEvent(Event event) {
        //do nothing
    }

}
