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
package com.alibaba.nacos.console.model;

/**
 * Namespace
 *
 * @author diamond
 */
public class Namespace {

    private String namespace;

    private String namespaceShowName;

    private int quota;

    private int configCount;
    /**
     * 0 : Global configuration， 1 : Default private namespace ，2 : Custom namespace
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
