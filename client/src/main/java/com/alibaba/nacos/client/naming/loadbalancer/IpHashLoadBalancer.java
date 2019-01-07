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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Ip-hash Load-Balancer Implementation
 * same ip will choose same index, It will be some problems when instances changed.
 * @author XCXCXCXCX
 */
public class IpHashLoadBalancer extends BaseLoadBalancer {

    public IpHashLoadBalancer(String serviceName, List<String> clusters, HostReactor hostReactor, EventDispatcher eventDispatcher) {
        super(serviceName, clusters, hostReactor, eventDispatcher, Boolean.TRUE);
    }

    public Instance doChoose(final ServiceInfo serviceInfo) {
        return doChoose0(serviceInfo.getHosts());
    }

    public Instance doChoose0(List<Instance> instances) {
        if(instances == null || instances.size() == 0){
            return null;
        }
        String host = null;
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        return instances.get(Math.abs(host.hashCode() % instances.size()));
    }

}
