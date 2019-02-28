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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.core.EventDispatcher;
import com.alibaba.nacos.client.naming.core.HostReactor;

import java.util.List;

/**
 * Random-With-Weight Load-Balancer Implementation
 * @author XCXCXCXCX
 */
public class RandomByWeightLoadBalancer extends BaseLoadBalancer {

    public RandomByWeightLoadBalancer(String serviceName, List<String> clusters, HostReactor hostReactor, Boolean enableListener) {
        super(serviceName, clusters, hostReactor, enableListener);
    }

    @Override
    public Instance doChoose(final ServiceInfo serviceInfo) {
        return Balancer.getHostByRandomWeight(serviceInfo);
    }

}
