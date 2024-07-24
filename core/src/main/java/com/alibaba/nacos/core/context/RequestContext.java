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

package com.alibaba.nacos.core.context;

import com.alibaba.nacos.core.context.addition.AuthContext;
import com.alibaba.nacos.core.context.addition.BasicContext;
import com.alibaba.nacos.core.context.addition.EngineContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nacos request context.
 *
 * @author xiweng.yy
 */
public class RequestContext {
    
    /**
     * Optional, the request id.
     * <ul>
     *     <li>For HTTP request, the id not usage, will generate automatically.</li>
     *     <li>For GRPC, the id is same with real request id.</li>
     * </ul>
     */
    private String requestId;
    
    /**
     * Request start timestamp.
     */
    private final long requestTimestamp;
    
    private final BasicContext basicContext;
    
    private final EngineContext engineContext;
    
    private final AuthContext authContext;
    
    private final Map<String, Object> extensionContexts;
    
    RequestContext(long requestTimestamp) {
        this.requestId = UUID.randomUUID().toString();
        this.requestTimestamp = requestTimestamp;
        this.basicContext = new BasicContext();
        this.engineContext = new EngineContext();
        this.authContext = new AuthContext();
        this.extensionContexts = new HashMap<>(1);
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public long getRequestTimestamp() {
        return requestTimestamp;
    }
    
    public BasicContext getBasicContext() {
        return basicContext;
    }
    
    public EngineContext getEngineContext() {
        return engineContext;
    }
    
    public AuthContext getAuthContext() {
        return authContext;
    }
    
    public Object getExtensionContext(String key) {
        return extensionContexts.get(key);
    }
    
    public void addExtensionContext(String key, Object value) {
        extensionContexts.put(key, value);
    }
}
