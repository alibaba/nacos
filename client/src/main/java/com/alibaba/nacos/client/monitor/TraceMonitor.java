package com.alibaba.nacos.client.monitor;

import com.alibaba.nacos.common.utils.VersionUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

public class TraceMonitor {
    
    private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("Nacos-Java-Client", VersionUtils.version);
    
    public enum RestfulMethod {
        GET,
        POST,
        DELETE,
        PUT;
        
        RestfulMethod() {
        }
    }
    
    public static Span getClientConfigRpcSpan() {
        return TRACER.spanBuilder("nacos.client.config.rpc").setSpanKind(SpanKind.CLIENT)
                .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
    }
    
    /**
     * Get the Nacos client config http span.
     *
     * @param method the http method
     * @return the OpenTelemetry span
     * @throws IllegalArgumentException if the method is not supported
     */
    public static Span getClientConfigHttpSpan(RestfulMethod method) throws IllegalArgumentException {
        switch (method) {
            case GET:
                return TRACER.spanBuilder("nacos.client.config.http.get").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case POST:
                return TRACER.spanBuilder("nacos.client.config.http.post").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case DELETE:
                return TRACER.spanBuilder("nacos.client.config.http.delete").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case PUT:
                return TRACER.spanBuilder("nacos.client.config.http.put").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            default:
                throw new IllegalArgumentException("Unsupported http method: " + method);
        }
    }
    
    public static Span getClientNamingRpcSpan() {
        return TRACER.spanBuilder("nacos.client.naming.rpc").setSpanKind(SpanKind.CLIENT)
                .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
    }
    
    /**
     * Get the Nacos client naming http span.
     *
     * @param method the http method
     * @return the OpenTelemetry span
     * @throws IllegalArgumentException if the method is not supported
     */
    public static Span getClientNamingHttpSpan(RestfulMethod method) throws IllegalArgumentException {
        switch (method) {
            case GET:
                return TRACER.spanBuilder("nacos.client.naming.http.get").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case POST:
                return TRACER.spanBuilder("nacos.client.naming.http.post").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case DELETE:
                return TRACER.spanBuilder("nacos.client.naming.http.delete").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case PUT:
                return TRACER.spanBuilder("nacos.client.naming.http.put").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            default:
                throw new IllegalArgumentException("Unsupported http method: " + method);
        }
    }
    
    /**
     * Get the Nacos client OpenTelemetry tracer.
     *
     * @return the OpenTelemetry tracer
     */
    public static Tracer getTracer() {
        return TRACER;
    }
}
