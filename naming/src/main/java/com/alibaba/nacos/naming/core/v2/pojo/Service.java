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

package com.alibaba.nacos.naming.core.v2.pojo;

import com.alibaba.nacos.api.naming.utils.NamingUtils;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service POJO for Nacos v2.
 *
 * @author xiweng.yy
 */
public class Service implements Serializable {
    
    private static final long serialVersionUID = -990509089519499344L;
    
    private final String namespace;
    
    private final String group;
    
    private final String name;
    
    private final boolean ephemeral;
    
    private final AtomicLong revision;
    
    private long lastUpdatedTime;
    
    private Service(String namespace, String group, String name, boolean ephemeral) {
        this.namespace = namespace;
        this.group = group;
        this.name = name;
        this.ephemeral = ephemeral;
        revision = new AtomicLong();
        lastUpdatedTime = System.currentTimeMillis();
    }
    
    public static Service newService(String namespace, String group, String name) {
        return newService(namespace, group, name, true);
    }
    
    public static Service newService(String namespace, String group, String name, boolean ephemeral) {
        return new Service(namespace, group, name, ephemeral);
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getGroup() {
        return group;
    }
    
    public String getName() {
        return name;
    }
    
    public boolean isEphemeral() {
        return ephemeral;
    }
    
    public long getRevision() {
        return revision.get();
    }
    
    public long getLastUpdatedTime() {
        return lastUpdatedTime;
    }
    
    public void renewUpdateTime() {
        lastUpdatedTime = System.currentTimeMillis();
    }
    
    public void incrementRevision() {
        revision.incrementAndGet();
    }
    
    public String getGroupedServiceName() {
        return NamingUtils.getGroupedName(name, group);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Service)) {
            return false;
        }
        Service service = (Service) o;
        return namespace.equals(service.namespace) && group.equals(service.group) && name.equals(service.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespace, group, name);
    }
    
    @Override
    public String toString() {
        return "Service{" + "namespace='" + namespace + '\'' + ", group='" + group + '\'' + ", name='" + name + '\''
                + ", ephemeral=" + ephemeral + ", revision=" + revision + '}';
    }
}
