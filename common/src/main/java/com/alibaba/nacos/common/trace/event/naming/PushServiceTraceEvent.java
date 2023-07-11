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

/**
 * Naming push service trace event.
 * @author yanda
 */
public class PushServiceTraceEvent extends NamingTraceEvent {
    
    private static final long serialVersionUID = 787915741281241877L;
    
    private final String clientIp;
    
    private final int instanceSize;
    
    private final long pushCostTimeForNetWork;
    
    private final long pushCostTimeForAll;
    
    private final long serviceLevelAgreementTime;
    
    public String getClientIp() {
        return clientIp;
    }
    
    public int getInstanceSize() {
        return instanceSize;
    }
    
    public long getPushCostTimeForNetWork() {
        return pushCostTimeForNetWork;
    }
    
    public long getPushCostTimeForAll() {
        return pushCostTimeForAll;
    }
    
    public long getServiceLevelAgreementTime() {
        return serviceLevelAgreementTime;
    }
    
    public PushServiceTraceEvent(long eventTime, long pushCostTimeForNetWork, long pushCostTimeForAll,
            long serviceLevelAgreementTime, String clientIp, String serviceNamespace,
            String serviceGroup, String serviceName, int instanceSize) {
        super("PUSH_SERVICE_TRACE_EVENT", eventTime, serviceNamespace, serviceGroup, serviceName);
        this.clientIp = clientIp;
        this.instanceSize = instanceSize;
        this.pushCostTimeForAll = pushCostTimeForAll;
        this.pushCostTimeForNetWork = pushCostTimeForNetWork;
        this.serviceLevelAgreementTime = serviceLevelAgreementTime;
    }
}
