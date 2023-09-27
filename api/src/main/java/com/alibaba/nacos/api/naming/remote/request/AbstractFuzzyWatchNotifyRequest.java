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

package com.alibaba.nacos.api.naming.remote.request;

import com.alibaba.nacos.api.remote.request.ServerRequest;

import static com.alibaba.nacos.api.common.Constants.Naming.NAMING_MODULE;

/**
 * Abstract fuzzy watch notify request, including basic fuzzy watch notify information.
 *
 * @author tanyongquan
 */
public abstract class AbstractFuzzyWatchNotifyRequest extends ServerRequest {
    private String namespace;
    
    private String pattern;
    
    private String serviceChangedType;
    
    public AbstractFuzzyWatchNotifyRequest(){
    }
    
    public AbstractFuzzyWatchNotifyRequest(String namespace, String pattern, String serviceChangedType) {
        this.namespace = namespace;
        this.pattern = pattern;
        this.serviceChangedType = serviceChangedType;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public String getServiceChangedType() {
        return serviceChangedType;
    }
    
    public void setServiceChangedType(String serviceChangedType) {
        this.serviceChangedType = serviceChangedType;
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
    @Override
    public String getModule() {
        return NAMING_MODULE;
    }
}
