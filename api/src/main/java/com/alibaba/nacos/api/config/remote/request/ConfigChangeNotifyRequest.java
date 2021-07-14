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

package com.alibaba.nacos.api.config.remote.request;

import com.alibaba.nacos.api.remote.request.ServerRequest;

/**
 * ConfigChangeNotifyRequest.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifyRequest.java, v 0.1 2020年07月14日 3:20 PM liuzunfei Exp $
 */
public class ConfigChangeNotifyRequest extends ServerRequest {
    
    private static final String MODULE = "config";
    
    String dataId;
    
    String group;
    
    String tenant;
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    /**
     * build success response.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return ConfigChangeNotifyResponse
     */
    public static ConfigChangeNotifyRequest build(String dataId, String group, String tenant) {
        ConfigChangeNotifyRequest request = new ConfigChangeNotifyRequest();
        request.setDataId(dataId);
        request.setGroup(group);
        request.setTenant(tenant);
        return request;
    }
    
    @Override
    public String getModule() {
        return MODULE;
    }
}
