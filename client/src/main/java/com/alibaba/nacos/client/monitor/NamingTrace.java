package com.alibaba.nacos.client.monitor;

import com.alibaba.nacos.common.utils.VersionUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;

public class NamingTrace {
    
    private static final String NACOS_CLIENT_NAMING_BASE_SPAN = "nacos.client.naming";
    
    private static final String NACOS_CLIENT_NAMING_RPC_SPAN = NACOS_CLIENT_NAMING_BASE_SPAN + ".rpc";
    
    private static final String NACOS_CLIENT_NAMING_HTTP_SPAN = NACOS_CLIENT_NAMING_BASE_SPAN + ".http";
    
    private static final String NACOS_CLIENT_VERSION_ATTRIBUTE = "nacos.client.version";
    
    public static Span getClientNamingRpcSpan() {
        return TraceMonitor.getTracer().spanBuilder(NACOS_CLIENT_NAMING_RPC_SPAN).setSpanKind(SpanKind.CLIENT)
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
}
