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
 * Naming register instance trace event.
 *
 * @author xiweng.yy
 */
public class BatchRegisterInstanceTraceEvent extends RegisterInstanceTraceEvent {
    
    public BatchRegisterInstanceTraceEvent(long eventTime, String clientIp, boolean rpc, String serviceNamespace,
            String serviceGroup, String serviceName, String instanceIp, int instancePort) {
        super("BATCH_REGISTER_INSTANCE_TRACE_EVENT", eventTime, clientIp, rpc, serviceNamespace, serviceGroup,
                serviceName, instanceIp, instancePort);
    }
}