package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.naming.loadbalancer.LoadBalancer;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.core.EventDispatcher;
import com.alibaba.nacos.client.naming.core.HostReactor;

import java.util.List;

/**
 * Load-Balancer for wrapping user implementations
 *
 * @author XCXCXCXCX
 */
public class CustomLoadBalancer extends BaseLoadBalancer {

    private final LoadBalancer loadBalancer;

    public CustomLoadBalancer(String serviceName,
                              List<String> clusters,
                              HostReactor hostReactor,
                              EventDispatcher eventDispatcher,
                              LoadBalancer loadBalancer,
                              Boolean enableListener) {
        super(serviceName, clusters, hostReactor, eventDispatcher, enableListener);
        this.loadBalancer = loadBalancer;
    }

    /**
     * User can use ServiceInfo to control, ServiceInfo changed when instances changed
     *
     * @param serviceInfo
     * @return Instance
     */
    @Override
    public Instance doChoose(ServiceInfo serviceInfo) {
        return loadBalancer.doChoose(serviceInfo);
    }
}
