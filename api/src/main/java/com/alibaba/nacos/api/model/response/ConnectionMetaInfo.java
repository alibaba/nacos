/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.model.response;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Nacos client connection metadata information.
 *
 * @author Nacos
 */
public class ConnectionMetaInfo {
    
    String connectType;
    
    String clientIp;
    
    String remoteIp;
    
    int remotePort;
    
    int localPort;
    
    String version;
    
    String connectionId;
    
    Date createTime;
    
    long lastActiveTime;
    
    String appName;
    
    String namespaceId;
    
    private Map<String, String> labels = new HashMap<>();
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public String getRemoteIp() {
        return remoteIp;
    }
    
    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }
    
    public int getRemotePort() {
        return remotePort;
    }
    
    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }
    
    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public Date getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }
    
    public String getConnectType() {
        return connectType;
    }

    public void setConnectType(String connectType) {
        this.connectType = connectType;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
}
