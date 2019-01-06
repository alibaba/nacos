package com.alibaba.nacos.client.naming.loadbalancer;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.loadbalancer.LoadBalancer;
import com.alibaba.nacos.api.naming.loadbalancer.LoadBalancerEnum;

import static com.alibaba.nacos.api.naming.loadbalancer.LoadBalancerEnum.*;

/**
 * Nacos Default Load-Balancer Implementation
 * @author XCXCXCXCX
 */
public class LoadBalancerManager {

    public static LoadBalancer toLoadBalancer(LoadBalancerEnum balancerEnum) throws NacosException {
        if(balancerEnum == RANDOM){
            return new RandomLoadBalancer();

        }else if(balancerEnum == RANDOM_BY_WEIGHT){
            return new RandomByWeightLoadBalancer();

        }else if(balancerEnum == POLL){
            return new PollLoadBalancer();

        }else if(balancerEnum == POLL_BY_WEIGHT){
            return new PollByWeightLoadBalancer();

        }else if(balancerEnum == IP_HASH){
            return new IpHashLoadBalancer();
        }
        throw new NacosException(NacosException.INVALID_PARAM,
            "This strategy {" + balancerEnum.getDescription() + "} is not currently supported");
    }

}
