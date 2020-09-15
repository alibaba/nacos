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

package com.alibaba.nacos.config.server.model;

import java.io.Serializable;

/**
 * HistoryContext.
 *
 * @author Nacos
 */
public class HistoryContext implements Serializable {
    
    private static final long serialVersionUID = -8400843549603420766L;
    
    public String serverId;
    
    public String dataId;
    
    public String group;
    
    public String tenant;
    
    public boolean success;
    
    public int statusCode;
    
    public String statusMsg;
    
    public Page<ConfigHistoryInfo> configs;
    
    private String appName;
    
    public HistoryContext(String serverId, String dataId, String group, int statusCode, String statusMsg,
            Page<ConfigHistoryInfo> configs) {
        this.serverId = serverId;
        this.dataId = dataId;
        this.group = group;
        this.statusCode = statusCode;
        this.statusMsg = statusMsg;
        this.configs = configs;
        this.success = 200 == statusCode;
    }
    
    public HistoryContext() {
    }
    
    public String getServerId() {
        return serverId;
    }
    
    public void setServerId(String serverId) {
        this.serverId = serverId;
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
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
    
    public String getStatusMsg() {
        return statusMsg;
    }
    
    public void setStatusMsg(String statusMsg) {
        this.statusMsg = statusMsg;
    }
    
    public Page<ConfigHistoryInfo> getConfigs() {
        return configs;
    }
    
    public void setConfigs(Page<ConfigHistoryInfo> configs) {
        this.configs = configs;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
}
