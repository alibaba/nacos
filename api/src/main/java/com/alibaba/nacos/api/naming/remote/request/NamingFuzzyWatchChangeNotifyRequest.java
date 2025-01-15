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

import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_RESOURCE_CHANGED;

/**
 * Nacos fuzzy watch notify service change request, use it when one of the services changes.
 *
 * @author tanyongquan
 */
public class NamingFuzzyWatchChangeNotifyRequest extends AbstractFuzzyWatchNotifyRequest {
    
    private String serviceKey;
    
    private String changedType;
    
    public NamingFuzzyWatchChangeNotifyRequest() {
    
    }
    
    public NamingFuzzyWatchChangeNotifyRequest(String serviceKey, String changedType) {
        super(FUZZY_WATCH_RESOURCE_CHANGED);
        this.serviceKey = serviceKey;
        this.changedType = changedType;
    }
    
    public String getServiceKey() {
        return serviceKey;
    }
    
    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }
    
    public String getChangedType() {
        return changedType;
    }
    
    public void setChangedType(String changedType) {
        this.changedType = changedType;
    }
}
