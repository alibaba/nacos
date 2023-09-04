package com.alibaba.nacos.client.monitor;

import com.alibaba.nacos.common.utils.VersionUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;

/**
 * Naming traces management.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class NamingTrace {
    
    private static final String NACOS_CLIENT_NAMING_BASE_SPAN = "nacos.client.naming";
    
    private static final String NACOS_CLIENT_NAMING_RPC_SPAN = NACOS_CLIENT_NAMING_BASE_SPAN + ".rpc";
    
    private static final String NACOS_CLIENT_NAMING_HTTP_SPAN = NACOS_CLIENT_NAMING_BASE_SPAN + ".http";
    
    private static final String NACOS_CLIENT_NAMING_SERVICE_SPAN = NACOS_CLIENT_NAMING_BASE_SPAN + ".service";
    
    private static final String NACOS_CLIENT_NAMING_WORKER_SPAN = NACOS_CLIENT_NAMING_BASE_SPAN + ".worker";
    
    private static final String NACOS_CLIENT_VERSION_ATTRIBUTE = "nacos.client.version";
    
    public static Span getClientNamingRpcSpan(String rpcType) {
        String spanName = NACOS_CLIENT_NAMING_RPC_SPAN + "." + rpcType;
        return TraceMonitor.getTracer().spanBuilder(spanName).setSpanKind(SpanKind.CLIENT)
                .setAttribute(NACOS_CLIENT_VERSION_ATTRIBUTE, VersionUtils.getFullClientVersion()).startSpan();
    }
    
    /**
     * Get the Nacos client naming http span.
     *
     * @param method the http method
     * @return the OpenTelemetry span
     */
    public static Span getClientNamingHttpSpan(String method) {
        String spanName = NACOS_CLIENT_NAMING_HTTP_SPAN + "." + method;
        return TraceMonitor.getTracer().spanBuilder(spanName).setSpanKind(SpanKind.CLIENT)
                .setAttribute(NACOS_CLIENT_VERSION_ATTRIBUTE, VersionUtils.getFullClientVersion()).startSpan();
    }
    
    public static Span getClientNamingServiceSpan(String spanNameExtension) {
        String spanName = NACOS_CLIENT_NAMING_SERVICE_SPAN + "." + spanNameExtension;
        return TraceMonitor.getTracer().spanBuilder(spanName).setSpanKind(SpanKind.CLIENT)
                .setAttribute(NACOS_CLIENT_VERSION_ATTRIBUTE, VersionUtils.getFullClientVersion()).startSpan();
    }
    
    public static Span getClientNamingWorkerSpan(String spanNameExtension) {
        String spanName = NACOS_CLIENT_NAMING_WORKER_SPAN + "." + spanNameExtension;
        return TraceMonitor.getTracer().spanBuilder(spanName).setSpanKind(SpanKind.CLIENT)
                .setAttribute(NACOS_CLIENT_VERSION_ATTRIBUTE, VersionUtils.getFullClientVersion()).startSpan();
    }
}
