package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.loadbalancer.LoadBalancer;
import com.alibaba.nacos.api.naming.loadbalancer.LoadBalancerEnum;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.core.EventDispatcher;
import com.alibaba.nacos.client.naming.core.HostReactor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.api.naming.loadbalancer.LoadBalancerEnum.*;

/**
 * Nacos Default Load-Balancer Implementation
 * @author XCXCXCXCX
 */
public class LoadBalancerManager {

    private static final String CUSTOM_LOAD_BALANCER_NAME = "custom";

    private static final Map<String, BaseLoadBalancer> loadBalancerCache =
        new ConcurrentHashMap<String, BaseLoadBalancer>();

    public static BaseLoadBalancer toLoadBalancer(String serviceName, List<String> clusters, LoadBalancerEnum balancerEnum,
                                                  final HostReactor hostReactor, final EventDispatcher eventDispatcher) throws NacosException {
        String key = serviceName + ServiceInfo.SPLITER + clusters.toString() + ServiceInfo.SPLITER + balancerEnum;
        BaseLoadBalancer loadBalancer = loadBalancerCache.get(key);
        if(loadBalancer == null){
            if(balancerEnum == RANDOM){
                loadBalancer = new RandomLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher);

            }else if(balancerEnum == RANDOM_BY_WEIGHT){
                loadBalancer = new RandomByWeightLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher);

            }else if(balancerEnum == POLL){
                loadBalancer = new PollLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher);

            }else if(balancerEnum == POLL_BY_WEIGHT){
                loadBalancer = new PollByWeightLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher);

            }else if(balancerEnum == IP_HASH){
                loadBalancer = new IpHashLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher);
            }else{
                throw new NacosException(NacosException.INVALID_PARAM,
                    "This strategy {" + balancerEnum.getDescription() + "} is not currently supported");
            }
            loadBalancerCache.put(loadBalancer.getKey() + balancerEnum, loadBalancer);
        }
        return loadBalancer;

    }

    public static BaseLoadBalancer wrapLoadBalancer(String serviceName, List<String> clusters,
                                                    final HostReactor hostReactor, final EventDispatcher eventDispatcher,
                                                    LoadBalancer loadBalancer, Boolean enableListener){
        String key = serviceName + ServiceInfo.SPLITER + clusters.toString() + ServiceInfo.SPLITER + CUSTOM_LOAD_BALANCER_NAME;
        BaseLoadBalancer baseLoadBalancer = loadBalancerCache.get(key);
        if(baseLoadBalancer == null){
            baseLoadBalancer = new CustomLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher, loadBalancer, enableListener);
            loadBalancerCache.put(baseLoadBalancer.getKey() + CUSTOM_LOAD_BALANCER_NAME ,baseLoadBalancer);
        }

        return baseLoadBalancer;
    }
}
