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
 * @deprecated use {@link com.alibaba.nacos.api.model.response.Namespace} replaced
 */
@Deprecated
public class Namespace extends com.alibaba.nacos.api.model.response.Namespace {
    
    public Namespace() {
        super();
    }
    
    public Namespace(String namespace, String namespaceShowName) {
        super(namespace, namespaceShowName);
    }
    
    public Namespace(String namespace, String namespaceShowName, int quota, int configCount, int type) {
        super(namespace, namespaceShowName, quota, configCount, type);
    }
    
    public Namespace(String namespace, String namespaceShowName, String namespaceDesc, int quota, int configCount,
            int type) {
        super(namespace, namespaceShowName, namespaceDesc, quota, configCount, type);
    }
}
