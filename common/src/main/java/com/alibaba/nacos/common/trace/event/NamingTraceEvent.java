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

package com.alibaba.nacos.common.trace.event;

import com.alibaba.nacos.common.trace.DeregisterInstanceReason;
import com.alibaba.nacos.common.trace.HealthStateChangeReason;

/**
 * Naming trace event.
 *
 * @author yanda
 */
public class NamingTraceEvent extends TraceEvent {

    private static final long serialVersionUID = 2923077640400851816L;
    
    public NamingTraceEvent(long eventTime, String serviceNamespace, String serviceGroup, String name) {
        super(eventTime, serviceNamespace, serviceGroup, name);
    }

    /**
     * Naming register instance trace event.
     */
    public static class RegisterInstanceTraceEvent extends NamingTraceEvent {
    
        private static final long serialVersionUID = -8283438151444483864L;
    
        private final String clientIp;

        private final boolean rpc;

        private final String instanceIp;
    
        public String getClientIp() {
            return clientIp;
        }
    
        public boolean isRpc() {
            return rpc;
        }

        public String getInstanceIp() {
            return instanceIp;
        }

        public RegisterInstanceTraceEvent(long eventTime, String clientIp, boolean rpc, String serviceNamespace,
                                          String serviceGroup, String serviceName, String instanceIp) {
            super(eventTime, serviceNamespace, serviceGroup, serviceName);
            this.clientIp = clientIp;
            this.rpc = rpc;
            this.instanceIp = instanceIp;
        }
    }

    /**
     * Naming deregister instance trace event.
     */
    public static class DeregisterInstanceTraceEvent extends NamingTraceEvent {
    
        private static final long serialVersionUID = 3850573686472190256L;
    
        private final String clientIp;

        private final boolean rpc;

        private final String instanceIp;
        
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
    
        public DeregisterInstanceReason getReason() {
            return reason;
        }
    
        public DeregisterInstanceTraceEvent(long eventTime, String clientIp, boolean rpc, DeregisterInstanceReason reason,
                String serviceNamespace, String serviceGroup, String serviceName, String instanceIp) {
            super(eventTime, serviceNamespace, serviceGroup, serviceName);
            this.clientIp = clientIp;
            this.reason = reason;
            this.rpc = rpc;
            this.instanceIp = instanceIp;
        }
    }

    /**
     * Naming deregister service trace event.
     */
    public static class RegisterServiceTraceEvent extends NamingTraceEvent {
    
        private static final long serialVersionUID = -8568231862586636388L;

        public RegisterServiceTraceEvent(long eventTime, String serviceNamespace,
                String serviceGroup, String serviceName) {
            super(eventTime, serviceNamespace, serviceGroup, serviceName);
        }
    }

    /**
     * Naming deregister service trace event.
     */
    public static class DeregisterServiceTraceEvent extends NamingTraceEvent {
    
        private static final long serialVersionUID = 7358195336881398548L;

        public DeregisterServiceTraceEvent(long eventTime, String serviceNamespace,
                                         String serviceGroup, String serviceName) {
            super(eventTime, serviceNamespace, serviceGroup, serviceName);
        }
    }

    /**
     * Naming subscribe service trace event.
     */
    public static class SubscribeServiceTraceEvent extends NamingTraceEvent {
    
        private static final long serialVersionUID = -8856834879168816801L;
    
        private final String clientIp;
    
        public String getClientIp() {
            return clientIp;
        }

        public SubscribeServiceTraceEvent(long eventTime, String clientIp, String serviceNamespace,
                                          String serviceGroup, String serviceName) {
            super(eventTime, serviceNamespace, serviceGroup, serviceName);
            this.clientIp = clientIp;
        }
    }

    /**
     * Naming unsubscribe service trace event.
     */
    public static class UnsubscribeServiceTraceEvent extends NamingTraceEvent {
    
        private static final long serialVersionUID = -7461808613817897106L;
    
        private final String clientIp;
    
        public String getClientIp() {
            return clientIp;
        }

        public UnsubscribeServiceTraceEvent(long eventTime, String clientIp, String serviceNamespace,
                                            String serviceGroup, String serviceName) {
            super(eventTime, serviceNamespace, serviceGroup, serviceName);
            this.clientIp = clientIp;
        }
    }
    
    /**
     * Naming push service trace event.
     */
    public static class PushServiceTraceEvent extends NamingTraceEvent {
    
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
            super(eventTime, serviceNamespace, serviceGroup, serviceName);
            this.clientIp = clientIp;
            this.instanceSize = instanceSize;
            this.pushCostTimeForAll = pushCostTimeForAll;
            this.pushCostTimeForNetWork = pushCostTimeForNetWork;
            this.serviceLevelAgreementTime = serviceLevelAgreementTime;
        }
    }
    
    /**
     * Naming instance http heartbeat timeout trace event.
     */
    public static class HealthStateChangeTraceEvent extends NamingTraceEvent {
    
        private static final long serialVersionUID = 6966396191118694597L;
    
        private final String instanceIp;

        private boolean isHealthy;
        
        private HealthStateChangeReason reason;
        
        public String getInstanceIp() {
            return instanceIp;
        }
    
        public boolean isHealthy() {
            return isHealthy;
        }
    
        public HealthStateChangeReason getReason() {
            return reason;
        }
    
        public HealthStateChangeTraceEvent(long eventTime, String serviceNamespace, String serviceGroup,
                String serviceName, String instanceIp, boolean isHealthy, HealthStateChangeReason reason) {
            super(eventTime, serviceNamespace, serviceGroup, serviceName);
            this.instanceIp = instanceIp;
            this.isHealthy = isHealthy;
            this.reason = reason;
        }
    }
}
