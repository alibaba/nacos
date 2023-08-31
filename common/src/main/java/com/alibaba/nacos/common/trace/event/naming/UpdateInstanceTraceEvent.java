package com.alibaba.nacos.common.trace.event.naming;

import com.alibaba.nacos.common.trace.DeregisterInstanceReason;

/**
 * Naming update instance trace event.
 *
 * @author stone-98
 * @date 2023/8/31
 */
public class UpdateInstanceTraceEvent extends NamingTraceEvent {
    
    private static final long serialVersionUID = -6995370254824508523L;
    
    private final String clientIp;
    
    private final boolean rpc;
    
    private final DeregisterInstanceReason reason;
    
    private String instanceIp;
    
    private int instancePort;
    
    public UpdateInstanceTraceEvent(long eventTime, String clientIp, boolean rpc, DeregisterInstanceReason reason,
            String serviceNamespace, String serviceGroup, String serviceName, String instanceIp, int instancePort) {
        super("UPDATE_INSTANCE_TRACE_EVENT", eventTime, serviceNamespace, serviceGroup, serviceName);
        this.clientIp = clientIp;
        this.reason = reason;
        this.rpc = rpc;
        this.instanceIp = instanceIp;
        this.instancePort = instancePort;
    }
    
}