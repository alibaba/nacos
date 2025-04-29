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

package com.alibaba.nacos.api.naming.pojo.maintainer;

import java.io.Serializable;

/**
 * Nacos naming {@link com.alibaba.nacos.naming.core.v2.client.Client} summary information.
 *
 * @author xiweng.yy
 */
public class ClientSummaryInfo implements Serializable {
    
    private static final long serialVersionUID = -4482158251664716884L;
    
    private String clientId;
    
    private boolean ephemeral;
    
    private long lastUpdatedTime;
    
    /**
     * The type of client, `connection` for upper 2.0 client, otherwise is `ipPort`.
     */
    private String clientType;
    
    /**
     * Following fields are only for `connection` {@link #clientType}.
     */
    private String connectType;
    
    private String appName;
    
    private String version;
    
    private String clientIp;
    
    private int clientPort;
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public boolean isEphemeral() {
        return ephemeral;
    }
    
    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }
    
    public long getLastUpdatedTime() {
        return lastUpdatedTime;
    }
    
    public void setLastUpdatedTime(long lastUpdatedTime) {
        this.lastUpdatedTime = lastUpdatedTime;
    }
    
    public String getClientType() {
        return clientType;
    }
    
    public void setClientType(String clientType) {
        this.clientType = clientType;
    }
    
    public String getConnectType() {
        return connectType;
    }
    
    public void setConnectType(String connectType) {
        this.connectType = connectType;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public int getClientPort() {
        return clientPort;
    }
    
    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }
}
