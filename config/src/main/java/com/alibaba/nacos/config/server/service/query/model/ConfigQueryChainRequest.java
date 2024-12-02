/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.query.model;

import java.util.Map;
import java.util.Objects;

/**
 * ConfigQueryChainRequest.
 *
 * @author Nacos
 */
public class ConfigQueryChainRequest {

    private String dataId;
    
    private String group;
    
    private String tenant;
    
    private String tag;
    
    private Map<String, String> appLabels;
    
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
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public Map<String, String> getAppLabels() {
        return appLabels;
    }
    
    public void setAppLabels(Map<String, String> appLabels) {
        this.appLabels = appLabels;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigQueryChainRequest that = (ConfigQueryChainRequest) o;
        return Objects.equals(dataId, that.dataId)
                && Objects.equals(group, that.group)
                && Objects.equals(tenant, that.tenant)
                && Objects.equals(tag, that.tag)
                && Objects.equals(appLabels, that.appLabels);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(dataId, group, tenant, tag, appLabels);
    }
}