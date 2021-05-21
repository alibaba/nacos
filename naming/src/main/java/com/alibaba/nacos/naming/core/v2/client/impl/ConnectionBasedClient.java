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

package com.alibaba.nacos.naming.core.v2.client.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.core.v2.client.AbstractClient;

/**
 * Nacos naming client based on tcp session.
 *
 * <p>The client is bind to the tcp session. When the tcp session disconnect, the client should be clean.
 *
 * @author xiweng.yy
 */
public class ConnectionBasedClient extends AbstractClient {
    
    private final String connectionId;
    
    /**
     * {@code true} means this client is directly connect to current server. {@code false} means this client is synced
     * from other server.
     */
    private final boolean isNative;
    
    /**
     * Only has meaning when {@code isNative} is false, which means that the last time verify from source server.
     */
    private volatile long lastRenewTime;
    
    public ConnectionBasedClient(String connectionId, boolean isNative) {
        super();
        this.connectionId = connectionId;
        this.isNative = isNative;
        lastRenewTime = getLastUpdatedTime();
    }
    
    @Override
    public String getClientId() {
        return connectionId;
    }
    
    @Override
    public boolean isEphemeral() {
        return true;
    }
    
    public boolean isNative() {
        return isNative;
    }
    
    public long getLastRenewTime() {
        return lastRenewTime;
    }
    
    public void setLastRenewTime() {
        this.lastRenewTime = System.currentTimeMillis();
    }
    
    @Override
    public boolean isExpire(long currentTime) {
        return !isNative() && currentTime - getLastRenewTime() > Constants.DEFAULT_IP_DELETE_TIMEOUT;
    }
}
