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

package com.alibaba.nacos.naming.healthcheck.heartbeat;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.interceptor.Interceptable;

import java.util.LinkedList;
import java.util.List;

/**
 * Instance beat check task.
 *
 * @author xiweng.yy
 */
public class InstanceBeatCheckTask implements Interceptable {
    
    private static final List<InstanceBeatChecker> CHECKERS = new LinkedList<>();
    
    private final IpPortBasedClient client;
    
    private final Service service;
    
    private final HealthCheckInstancePublishInfo instancePublishInfo;
    
    static {
        CHECKERS.add(new UnhealthyInstanceChecker());
        CHECKERS.add(new ExpiredInstanceChecker());
        CHECKERS.addAll(NacosServiceLoader.load(InstanceBeatChecker.class));
    }
    
    public InstanceBeatCheckTask(IpPortBasedClient client, Service service, HealthCheckInstancePublishInfo instancePublishInfo) {
        this.client = client;
        this.service = service;
        this.instancePublishInfo = instancePublishInfo;
    }
    
    @Override
    public void passIntercept() {
        for (InstanceBeatChecker each : CHECKERS) {
            each.doCheck(client, service, instancePublishInfo);
        }
    }
    
    @Override
    public void afterIntercept() {
    }
    
    public IpPortBasedClient getClient() {
        return client;
    }
    
    public Service getService() {
        return service;
    }
    
    public HealthCheckInstancePublishInfo getInstancePublishInfo() {
        return instancePublishInfo;
    }
}
