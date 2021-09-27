/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.metadata;

import java.io.Serializable;

/**
 * Metadata operation.
 *
 * @author xiweng.yy
 */
public class MetadataOperation<T> implements Serializable {
    
    private static final long serialVersionUID = -111405695252896706L;
    
    private String namespace;
    
    private String group;
    
    private String serviceName;
    
    /**
     * If the metadata is cluster or instance, the tag should be added with the identity of cluster or instance.
     */
    private String tag;
    
    private T metadata;
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public T getMetadata() {
        return metadata;
    }
    
    public void setMetadata(T metadata) {
        this.metadata = metadata;
    }
}
