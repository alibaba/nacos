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

package com.alibaba.nacos.client.naming.event;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.notify.Event;

import java.util.List;

/**
 * Instances change event.
 *
 * @author horizonzy
 * @since 1.4.1
 */
public class InstancesChangeEvent extends Event {
    
    private static final long serialVersionUID = -8823087028212249603L;
    
    private final String serviceName;
    
    private final String groupName;
    
    private final String clusters;
    
    private final List<Instance> hosts;
    
    public InstancesChangeEvent(String serviceName, String groupName, String clusters, List<Instance> hosts) {
        this.serviceName = serviceName;
        this.groupName = groupName;
        this.clusters = clusters;
        this.hosts = hosts;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public String getClusters() {
        return clusters;
    }
    
    public List<Instance> getHosts() {
        return hosts;
    }
    
}
