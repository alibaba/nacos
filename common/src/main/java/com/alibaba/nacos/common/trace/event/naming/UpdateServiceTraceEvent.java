package com.alibaba.nacos.common.trace.event.naming;

/**
 * Naming update service trace event.
 *
 * @author stone-98
 * @date 2023/8/31
 */
public class UpdateServiceTraceEvent extends NamingTraceEvent {
    
    private static final long serialVersionUID = -6792054530665003857L;
    
    public UpdateServiceTraceEvent(long eventTime, String serviceNamespace, String serviceGroup, String serviceName) {
        super("UPDATE_SERVICE_TRACE_EVENT", eventTime, serviceNamespace, serviceGroup, serviceName);
    }
}