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

package com.alibaba.nacos.plugin.control.connection.request;

import com.alibaba.nacos.plugin.control.context.ControlContext;

import java.util.Map;

/**
 * connection check request.
 *
 * @author shiyiyue
 */
public class ConnectionCheckRequest {
    
    String clientIp;
    
    String appName;
    
    String source;
    
    Map<String, String> labels;
    
    private ControlContext controlContext = ControlContext.empty();
    
    public ConnectionCheckRequest(String clientIp, String appName, String source) {
        this.appName = appName;
        this.clientIp = clientIp;
        this.source = source;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
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
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
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
