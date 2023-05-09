/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.model;

/**
 * Namespace.
 *
 * @author diamond
 */
public class Namespace {
    
    private String namespace;
    
    private String namespaceShowName;
    
    private String namespaceDesc;
    
    private int quota;
    
    private int configCount;
    
    /**
     * see {@link NamespaceTypeEnum}.
     */
    private int type;
    
    public String getNamespaceShowName() {
        return namespaceShowName;
    }
    
    public void setNamespaceShowName(String namespaceShowName) {
        this.namespaceShowName = namespaceShowName;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public Namespace() {
    }
    
    public Namespace(String namespace, String namespaceShowName) {
        this.namespace = namespace;
        this.namespaceShowName = namespaceShowName;
    }
    
    public Namespace(String namespace, String namespaceShowName, int quota, int configCount, int type) {
        this.namespace = namespace;
        this.namespaceShowName = namespaceShowName;
        this.quota = quota;
        this.configCount = configCount;
        this.type = type;
    }
    
    public Namespace(String namespace, String namespaceShowName, String namespaceDesc, int quota, int configCount,
            int type) {
        this.namespace = namespace;
        this.namespaceShowName = namespaceShowName;
        this.quota = quota;
        this.configCount = configCount;
        this.type = type;
        this.namespaceDesc = namespaceDesc;
    }
    
    public String getNamespaceDesc() {
        return namespaceDesc;
    }
    
    public void setNamespaceDesc(String namespaceDesc) {
        this.namespaceDesc = namespaceDesc;
    }
    
    public int getQuota() {
        return quota;
    }
    
    public void setQuota(int quota) {
        this.quota = quota;
    }
    
    public int getConfigCount() {
        return configCount;
    }
    
    public void setConfigCount(int configCount) {
        this.configCount = configCount;
    }
    
    public int getType() {
        return type;
    }
    
    public void setType(int type) {
        this.type = type;
    }
    
}
