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

package com.alibaba.nacos.plugin.control.tps.request;

import com.alibaba.nacos.plugin.control.context.ControlContext;

/**
 * tps request.
 *
 * @author shiyiyue
 */
public class TpsCheckRequest {
    
    private String pointName;
    
    private long timestamp = System.currentTimeMillis();
    
    private String connectionId;
    
    private String clientIp;
    
    private long count = 1;
    
    private ControlContext controlContext = ControlContext.empty();
    
    public TpsCheckRequest() {
        
    }
    
    public TpsCheckRequest(String pointName, String connectionId, String clientIp) {
        this.connectionId = connectionId;
        this.clientIp = clientIp;
        this.pointName = pointName;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getConnectionId() {
        return connectionId;
    }
    
    public long getCount() {
        return count;
    }
    
    public void setCount(long count) {
        this.count = count;
    }
    
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    /**
     * Get the runtime context of this check. Never {@code null}.
     *
     * @return runtime context snapshot
     */
    public ControlContext getControlContext() {
        return controlContext;
    }
    
    /**
     * Set the runtime context of this check. A {@code null} value resets the context to empty.
     *
     * @param controlContext runtime context snapshot
     */
    public void setControlContext(ControlContext controlContext) {
        this.controlContext = null == controlContext ? ControlContext.empty() : controlContext;
    }
    
}
