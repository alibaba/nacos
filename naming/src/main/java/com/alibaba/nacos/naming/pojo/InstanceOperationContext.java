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

package com.alibaba.nacos.naming.pojo;

import com.alibaba.nacos.naming.core.Instance;

import java.util.List;

/**
 * InstanceOperationContext. used in instance batch operation's consumer.
 *
 * @author horizonzy
 * @since 1.4.0
 */
public class InstanceOperationContext {
    
    public InstanceOperationContext() {
    }
    
    public InstanceOperationContext(String namespace, String serviceName, Boolean ephemeral, Boolean all) {
        this.namespace = namespace;
        this.serviceName = serviceName;
        this.ephemeral = ephemeral;
        this.all = all;
    }
    
    public InstanceOperationContext(String namespace, String serviceName, Boolean ephemeral, Boolean all,
            List<Instance> instances) {
        this.namespace = namespace;
        this.serviceName = serviceName;
        this.ephemeral = ephemeral;
        this.all = all;
        this.instances = instances;
    }
    
    private String namespace;
    
    private String serviceName;
    
    private Boolean ephemeral;
    
    private Boolean all;
    
    private List<Instance> instances;
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public Boolean getEphemeral() {
        return ephemeral;
    }
    
    public Boolean getAll() {
        return all;
    }
    
    public List<Instance> getInstances() {
        return instances;
    }
}
