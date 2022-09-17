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

package com.alibaba.nacos.istio.model;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author special.fy
 */
public class IstioService {
    
    private final String name;
    
    private final String groupName;
    
    private final String namespace;
    
    private final Long revision;
    
    private final Map<String, Integer> portsMap = new HashMap<>(16);
    
    private final List<IstioEndpoint> hosts;
    
    private final Date createTimeStamp;
    
    public IstioService(Service service, ServiceInfo serviceInfo) {
        this.name = serviceInfo.getName();
        this.groupName = serviceInfo.getGroupName();
        this.namespace = service.getNamespace();
        this.revision = service.getRevision();
        // Record the create time of service to avoid trigger istio pull push.
        // See https://github.com/istio/istio/pull/30684
        createTimeStamp = new Date();
        
        this.hosts = sanitizeServiceInfo(this, serviceInfo);
        
        for (IstioEndpoint istioEndpoint : this.hosts) {
            this.portsMap.put(istioEndpoint.getProtocol(), istioEndpoint.getPort());
        }
    }

    public IstioService(Service service, ServiceInfo serviceInfo, IstioService old) {
        this.name = serviceInfo.getName();
        this.groupName = serviceInfo.getGroupName();
        this.namespace = service.getNamespace();
        this.revision = service.getRevision();
        // set the create time of service as old time to avoid trigger istio pull push.
        // See https://github.com/istio/istio/pull/30684
        createTimeStamp = old.getCreateTimeStamp();
        
        this.hosts = sanitizeServiceInfo(this, serviceInfo);
        
        for (IstioEndpoint istioEndpoint : this.hosts) {
            this.portsMap.put(istioEndpoint.getProtocol(), istioEndpoint.getPort());
        }
    }

    private List<IstioEndpoint> sanitizeServiceInfo(IstioService istioService, ServiceInfo serviceInfo) {
        List<IstioEndpoint> hosts = new ArrayList<>();

        for (Instance instance : serviceInfo.getHosts()) {
            if (instance.isHealthy() && instance.isEnabled()) {
                IstioEndpoint istioEndpoint = new IstioEndpoint(instance, istioService);
                hosts.add(istioEndpoint);
            }
        }

        // Panic mode, all instances are invalid, to push all instances to istio.
        if (hosts.isEmpty()) {
            for (Instance instance : serviceInfo.getHosts()) {
                IstioEndpoint istioEndpoint = new IstioEndpoint(instance, istioService);
                hosts.add(istioEndpoint);
            }
        }

        return hosts;
    }

    public String getName() {
        return name;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getNamespace() {
        return namespace;
    }

    public Long getRevision() {
        return revision;
    }

    public Map<String, Integer> getPortsMap() {
        return portsMap;
    }
    
    public List<IstioEndpoint> getHosts() {
        return hosts;
    }

    public Date getCreateTimeStamp() {
        return createTimeStamp;
    }
}