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

import com.alibaba.nacos.common.trace.HealthCheckType;

/**
 * Naming instance health state change trace event.
 * @author yanda
 */
public class HealthStateChangeTraceEvent extends NamingTraceEvent {
    
    private static final long serialVersionUID = 6966396191118694597L;
    
    private String instanceIp;
    
    private int instancePort;
    
    private boolean isHealthy;
    
    private HealthCheckType healthCheckType;
    
    private String  healthStateChangeReason;
    
    public String getInstanceIp() {
        return instanceIp;
    }
    
    public int getInstancePort() {
        return instancePort;
    }
    
    public String toInetAddr() {
        return instanceIp + ":" + instancePort;
    }
    
    public boolean isHealthy() {
        return isHealthy;
    }
    
    public HealthCheckType getHealthCheckType() {
        return healthCheckType;
    }
    
    public String getHealthStateChangeReason() {
        return healthStateChangeReason;
    }
    
    public HealthStateChangeTraceEvent(long eventTime, String serviceNamespace, String serviceGroup,
            String serviceName, String instanceIp, int instancePort, boolean isHealthy, String healthStateChangeReason) {
        super("HEALTH_STATE_CHANGE_TRACE_EVENT", eventTime, serviceNamespace, serviceGroup, serviceName);
        this.instanceIp = instanceIp;
        this.instancePort = instancePort;
        this.isHealthy = isHealthy;
        this.healthCheckType = getHealthCheckTypeFromReason(healthStateChangeReason);
        this.healthStateChangeReason = healthStateChangeReason;
    }
    
    public HealthCheckType getHealthCheckTypeFromReason(String reason) {
        if (reason.startsWith(HealthCheckType.HTTP_HEALTH_CHECK.getPrefix())) {
            return HealthCheckType.HTTP_HEALTH_CHECK;
        } else if (reason.startsWith(HealthCheckType.TCP_SUPER_SENSE.getPrefix())) {
            return HealthCheckType.TCP_SUPER_SENSE;
        } else if (reason.startsWith(HealthCheckType.MYSQL_HEALTH_CHECK.getPrefix())) {
            return HealthCheckType.MYSQL_HEALTH_CHECK;
        }
        return HealthCheckType.CLIENT_BEAT;
    }
}
