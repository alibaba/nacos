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
 * all namespace info
 *
 * @author Nacos
 */
public class NamespaceAllInfo extends Namespace {

    private String namespaceDesc;

    public String getNamespaceDesc() {
        return namespaceDesc;
    }

    public void setNamespaceDesc(String namespaceDesc) {
        this.namespaceDesc = namespaceDesc;
    }

    public NamespaceAllInfo() {
    }

    public NamespaceAllInfo(String namespace, String namespaceShowName, int quota, int configCount, int type,
                            String namespaceDesc) {
        super(namespace, namespaceShowName, quota, configCount, type);
        this.namespaceDesc = namespaceDesc;
    }

}
