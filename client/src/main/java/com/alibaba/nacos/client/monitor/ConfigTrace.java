package com.alibaba.nacos.client.monitor;

import com.alibaba.nacos.common.utils.VersionUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;

/**
 * Config traces management.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class ConfigTrace {
    
    private static final String NACOS_CLIENT_CONFIG_BASE_SPAN = "nacos.client.config";
    
    private static final String NACOS_CLIENT_CONFIG_RPC_SPAN = NACOS_CLIENT_CONFIG_BASE_SPAN + ".rpc";
    
    private static final String NACOS_CLIENT_CONFIG_HTTP_SPAN = NACOS_CLIENT_CONFIG_BASE_SPAN + ".http";
    
    private static final String NACOS_CLIENT_CONFIG_SERVICE_SPAN = NACOS_CLIENT_CONFIG_BASE_SPAN + ".service";
    
    private static final String NACOS_CLIENT_CONFIG_WORKER_SPAN = NACOS_CLIENT_CONFIG_BASE_SPAN + ".worker";
    
    private static final String NACOS_CLIENT_VERSION_ATTRIBUTE = "nacos.client.version";
    
    public static Span getClientConfigRpcSpan(String rpcType) {
        String spanName = NACOS_CLIENT_CONFIG_RPC_SPAN + "." + rpcType;
        return TraceMonitor.getTracer().spanBuilder(spanName).setSpanKind(SpanKind.CLIENT)
                .setAttribute(NACOS_CLIENT_VERSION_ATTRIBUTE, VersionUtils.getFullClientVersion()).startSpan();
    }
    
    /**
     * Get the Nacos client config http span.
     *
     * @param method the http method
     * @return the OpenTelemetry span
     */
    public static Span getClientConfigHttpSpan(String method) {
        String spanName = NACOS_CLIENT_CONFIG_HTTP_SPAN + "." + method;
        return TraceMonitor.getTracer().spanBuilder(spanName).setSpanKind(SpanKind.CLIENT)
                .setAttribute(NACOS_CLIENT_VERSION_ATTRIBUTE, VersionUtils.getFullClientVersion()).startSpan();
    }
    
    public static Span getClientConfigServiceSpan(String spanNameExtension) {
        String spanName = NACOS_CLIENT_CONFIG_SERVICE_SPAN + "." + spanNameExtension;
        return TraceMonitor.getTracer().spanBuilder(spanName).setSpanKind(SpanKind.CLIENT)
                .setAttribute(NACOS_CLIENT_VERSION_ATTRIBUTE, VersionUtils.getFullClientVersion()).startSpan();
    }
    
    public static Span getClientConfigWorkerSpan(String spanNameExtension) {
        String spanName = NACOS_CLIENT_CONFIG_WORKER_SPAN + "." + spanNameExtension;
        return TraceMonitor.getTracer().spanBuilder(spanName).setSpanKind(SpanKind.CLIENT)
                .setAttribute(NACOS_CLIENT_VERSION_ATTRIBUTE, VersionUtils.getFullClientVersion()).startSpan();
    }
    
}
