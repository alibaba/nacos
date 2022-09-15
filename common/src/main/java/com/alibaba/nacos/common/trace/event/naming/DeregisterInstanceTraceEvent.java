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

import com.alibaba.nacos.common.trace.DeregisterInstanceReason;

/**
 * Naming deregister instance trace event.
 * @author yanda
 */
public class DeregisterInstanceTraceEvent extends NamingTraceEvent {
    
    private static final long serialVersionUID = 3850573686472190256L;
    
    private final String clientIp;
    
    private final boolean rpc;
    
    private String instanceIp;
    
    private int instancePort;
    
    public final DeregisterInstanceReason reason;
    
    public String getClientIp() {
        return clientIp;
    }
    
    public boolean isRpc() {
        return rpc;
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
    
    public DeregisterInstanceReason getReason() {
        return reason;
    }
    
    public DeregisterInstanceTraceEvent(long eventTime, String clientIp, boolean rpc, DeregisterInstanceReason reason,
            String serviceNamespace, String serviceGroup, String serviceName, String instanceIp, int instancePort) {
        super("DEREGISTER_INSTANCE_TRACE_EVENT", eventTime, serviceNamespace, serviceGroup, serviceName);
        this.clientIp = clientIp;
        this.reason = reason;
        this.rpc = rpc;
        this.instanceIp = instanceIp;
        this.instancePort = instancePort;
    }
}
