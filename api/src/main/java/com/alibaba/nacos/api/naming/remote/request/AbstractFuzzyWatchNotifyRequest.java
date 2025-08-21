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
    
    private String syncType;
    
    public AbstractFuzzyWatchNotifyRequest() {
    }
    
    public AbstractFuzzyWatchNotifyRequest(String syncType) {
        this.syncType = syncType;
    }
    
    public String getSyncType() {
        return syncType;
    }
    
    public void setSyncType(String syncType) {
        this.syncType = syncType;
    }
    
    @Override
    public String getModule() {
        return NAMING_MODULE;
    }
}
