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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.ConvertUtils;

import java.io.Serializable;
import java.util.Objects;
import java.util.StringJoiner;
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
    
    public void incrementRevision() {
        revision.incrementAndGet();
        lastUpdatedTime = System.currentTimeMillis();
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
    
    /**
     * Custom simple serialization method. so Protobuf, you have Index mapping
     *
     * @param service {@link Service}
     * @return bytes
     */
    public static byte[] easySerialize(Service service) {
        final StringJoiner joiner = new StringJoiner(";");
        joiner.add("1=" + service.namespace)
                .add("2=" + service.name)
                .add("3=" + service.group)
                .add("4=" + service.ephemeral)
                .add("5=" + service.lastUpdatedTime)
                .add("6=" + service.revision.get());
        return ByteUtils.toBytes(joiner.toString());
    }
    
    /**
     * Simple custom deserialization.
     *
     * @param bytes byte[]
     * @return {@link Service}
     */
    public static Service easyDeserialize(final byte[] bytes) {
        final String[] ss = ByteUtils.toString(bytes).split(";");
        String namespace = Constants.DEFAULT_NAMESPACE_ID;
        String name = "";
        String group = Constants.DEFAULT_GROUP;
        boolean ephemeral = true;
        long lastUpdatedTime = -1;
        long revision = 0;
        for (final String s : ss) {
            final String[] info = s.split("=");
            switch (info[0]) {
                case "1":
                    namespace = info[1];
                    break;
                case "2":
                    name = info[1];
                    break;
                case "3":
                    group = info[1];
                    break;
                case "4":
                    ephemeral = ConvertUtils.toBoolean(info[1]);
                    break;
                case "5":
                    lastUpdatedTime = ConvertUtils.toLong(info[1]);
                    break;
                case "6":
                    revision = ConvertUtils.toLong(info[1]);
                    break;
                default:
                    // do nothing
            }
        }
        final Service service = Service.newService(namespace, name, group, ephemeral);
        service.lastUpdatedTime = lastUpdatedTime;
        service.revision.set(revision);
        return service;
    }
}
