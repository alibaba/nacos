/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    private static final Map<String, BaseLoadBalancer> LOAD_BALANCER_CACHE =
        new ConcurrentHashMap<String, BaseLoadBalancer>();

    public static BaseLoadBalancer toLoadBalancer(String serviceName, List<String> clusters, LoadBalancerEnum balancerEnum,
                                                  final HostReactor hostReactor, final EventDispatcher eventDispatcher, Boolean enableListener) throws NacosException {
        String key = serviceName + ServiceInfo.SPLITER + clusters.toString() + ServiceInfo.SPLITER + balancerEnum;
        BaseLoadBalancer loadBalancer = LOAD_BALANCER_CACHE.get(key);
        if(loadBalancer == null){
            if(balancerEnum == RANDOM){
                loadBalancer = new RandomLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher, enableListener);

            }else if(balancerEnum == RANDOM_BY_WEIGHT){
                loadBalancer = new RandomByWeightLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher, enableListener);

            }else if(balancerEnum == POLL){
                loadBalancer = new PollLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher, enableListener);

            }else if(balancerEnum == POLL_BY_WEIGHT){
                loadBalancer = new PollByWeightLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher, enableListener);

            }else if(balancerEnum == IP_HASH){
                loadBalancer = new IpHashLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher, enableListener);
            }else{
                throw new NacosException(NacosException.INVALID_PARAM,
                    "This strategy {" + balancerEnum.getDescription() + "} is not currently supported");
            }
            LOAD_BALANCER_CACHE.put(loadBalancer.getKey() + balancerEnum, loadBalancer);
        }
        return loadBalancer;

    }

    public static BaseLoadBalancer wrapLoadBalancer(String serviceName, List<String> clusters,
                                                    final HostReactor hostReactor, final EventDispatcher eventDispatcher,
                                                    LoadBalancer loadBalancer, Boolean enableListener){
        String key = serviceName + ServiceInfo.SPLITER + clusters.toString() + ServiceInfo.SPLITER + CUSTOM_LOAD_BALANCER_NAME;
        BaseLoadBalancer baseLoadBalancer = LOAD_BALANCER_CACHE.get(key);
        if(baseLoadBalancer == null){
            baseLoadBalancer = new CustomLoadBalancer(serviceName, clusters, hostReactor, eventDispatcher, loadBalancer, enableListener);
            LOAD_BALANCER_CACHE.put(baseLoadBalancer.getKey() + CUSTOM_LOAD_BALANCER_NAME ,baseLoadBalancer);
        }

        return baseLoadBalancer;
    }
}
