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

package com.alibaba.nacos.api.config;

import com.alibaba.nacos.api.utils.StringUtils;

/**
 * ConfigInfo.
 *
 * @author boyan
 * @date 2010-5-4
 */
public class ConfigInfo {
    
    static final long serialVersionUID = -1L;
    
    private String tenant;
    
    private String appName;
    
    private String type;
    
    private String dataId;
    
    private String group;
    
    private String content;
    
    public ConfigInfo() {
    }
    
    public ConfigInfo(String dataId, String group, String content) {
        this.dataId = dataId;
        this.group = group;
        this.content = content;
    }
    
    public ConfigInfo(String dataId, String group, String appName, String content) {
        this.dataId = dataId;
        this.group = group;
        this.content = content;
        this.appName = appName;
    }
    
    public ConfigInfo(String dataId, String group, String type, String appName, String content) {
        this.dataId = dataId;
        this.group = group;
        this.content = content;
        this.appName = appName;
        this.type = type;
    }
    
    public ConfigInfo(String dataId, String group, String tenant, String type, String appName, String content) {
        this.dataId = dataId;
        this.group = group;
        this.content = content;
        this.tenant = tenant;
        this.appName = appName;
        this.type = type;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getType() {
        if (StringUtils.isBlank(this.type)) {
            type = ConfigType.TEXT.getType();
        }
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
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
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    @Override
    public String toString() {
        return "ConfigInfo{dataId='" + getDataId() + '\'' + ", group='" + getGroup() + '\'' + ", tenant='" + tenant
                + '\'' + ", type='" + type + '\'' + ", appName='" + appName + '\'' + ", content='" + getContent() + '\''
                + '}';
    }
    
}
