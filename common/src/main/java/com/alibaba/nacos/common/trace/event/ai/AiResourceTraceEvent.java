/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.trace.event.ai;

import com.alibaba.nacos.common.trace.event.TraceEvent;

/**
 * AI resource trace event.
 *
 * @author nacos
 */
public class AiResourceTraceEvent extends TraceEvent {
    
    public static final String AI_RESOURCE_TRACE_EVENT = "AI_RESOURCE_TRACE_EVENT";
    
    private static final long serialVersionUID = -2114269686069278879L;
    
    private final String operator;
    
    private final String resourceType;
    
    private final String resourceId;
    
    private final String version;
    
    private final String operation;
    
    private final String status;
    
    private final String clientIp;
    
    private final String ext;
    
    public AiResourceTraceEvent(long eventTime, String operator, String resourceType,
        String resourceId, String version, String operation, String status, String clientIp,
        String ext) {
        super(AI_RESOURCE_TRACE_EVENT, eventTime, "", resourceType, resourceId);
        this.operator = operator;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.version = version;
        this.operation = operation;
        this.status = status;
        this.clientIp = clientIp;
        this.ext = ext;
    }
    
    public String getOperator() {
        return operator;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public String getResourceId() {
        return resourceId;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public String getStatus() {
        return status;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public String getExt() {
        return ext;
    }
    
    @Override
    public boolean isPluginEvent() {
        return true;
    }
}
