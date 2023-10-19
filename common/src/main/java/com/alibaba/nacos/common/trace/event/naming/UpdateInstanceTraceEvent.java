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

package com.alibaba.nacos.common.trace.event.naming;

import java.util.Map;

/**
 * Naming update instance trace event.
 *
 * @author stone-98
 * @date 2023/8/31
 */
public class UpdateInstanceTraceEvent extends NamingTraceEvent {
    
    private static final long serialVersionUID = -6995370254824508523L;
    
    private final Map<String, String> metadata;
    
    private final String clientIp;
    
    private final String instanceIp;
    
    private final int instancePort;
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public String getInstanceIp() {
        return instanceIp;
    }
    
    public int getInstancePort() {
        return instancePort;
    }
    
    public String toInetAddr() {
        return instanceIp + ":" + instancePort;
    }
    
    public UpdateInstanceTraceEvent(long eventTime, String clientIp, String serviceNamespace, String serviceGroup,
            String serviceName, String instanceIp, int instancePort, Map<String, String> metadata) {
        super("UPDATE_INSTANCE_TRACE_EVENT", eventTime, serviceNamespace, serviceGroup, serviceName);
        this.clientIp = clientIp;
        this.instanceIp = instanceIp;
        this.instancePort = instancePort;
        this.metadata = metadata;
    }
    
}