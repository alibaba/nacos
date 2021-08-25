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
import java.util.List;

/**
 * @author special.fy
 */
public class IstioService {

    private String name;

    private String groupName;

    private String namespace;

    private Long revision;

    private List<Instance> hosts;

    private Date createTimeStamp;

    public IstioService(Service service, ServiceInfo serviceInfo) {
        this.name = serviceInfo.getName();
        this.groupName = serviceInfo.getGroupName();
        this.namespace = service.getNamespace();
        this.revision = service.getRevision();
        // Record the create time of service to avoid trigger istio pull push.
        // See https://github.com/istio/istio/pull/30684
        createTimeStamp = new Date();

        this.hosts = sanitizeServiceInfo(serviceInfo);
    }

    public IstioService(Service service, ServiceInfo serviceInfo, IstioService old) {
        this.name = serviceInfo.getName();
        this.groupName = serviceInfo.getGroupName();
        this.namespace = service.getNamespace();
        this.revision = service.getRevision();
        // set the create time of service as old time to avoid trigger istio pull push.
        // See https://github.com/istio/istio/pull/30684
        createTimeStamp = old.getCreateTimeStamp();

        this.hosts = sanitizeServiceInfo(serviceInfo);
    }

    private List<Instance> sanitizeServiceInfo(ServiceInfo serviceInfo) {
        List<Instance> hosts = new ArrayList<>();

        for (Instance instance : serviceInfo.getHosts()) {
            if (instance.isHealthy() && instance.isEnabled()) {
                hosts.add(instance);
            }
        }

        // Panic mode, all instances are invalid, to push all instances to istio.
        if (hosts.isEmpty()) {
            hosts = serviceInfo.getHosts();
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

    public List<Instance> getHosts() {
        return hosts;
    }

    public Date getCreateTimeStamp() {
        return createTimeStamp;
    }
}
