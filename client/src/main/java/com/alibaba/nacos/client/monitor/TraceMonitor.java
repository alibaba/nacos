package com.alibaba.nacos.client.monitor;

import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.VersionUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

public class TraceMonitor {
    
    private static Tracer tracer = GlobalOpenTelemetry.getTracer("Nacos-Java-Client", VersionUtils.version);
    
    public static Span getClientConfigRpcSpan() {
        return tracer.spanBuilder("nacos.client.config.rpc").setSpanKind(SpanKind.CLIENT)
                .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
    }
    
    /**
     * Get the Nacos client config http span.
     *
     * @param method the http method
     * @return the OpenTelemetry span
     * @throws IllegalArgumentException if the method is not supported
     */
    public static Span getClientConfigHttpSpan(String method) throws IllegalArgumentException {
        switch (method) {
            case HttpMethod.GET:
                return tracer.spanBuilder("nacos.client.config.http.get").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case HttpMethod.POST:
                return tracer.spanBuilder("nacos.client.config.http.post").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case HttpMethod.DELETE:
                return tracer.spanBuilder("nacos.client.config.http.delete").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case HttpMethod.PUT:
                return tracer.spanBuilder("nacos.client.config.http.put").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            default:
                throw new IllegalArgumentException("Unsupported http method: " + method);
        }
    }
    
    public static Span getClientNamingRpcSpan() {
        return tracer.spanBuilder("nacos.client.naming.rpc").setSpanKind(SpanKind.CLIENT)
                .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
    }
    
    /**
     * Get the Nacos client naming http span.
     *
     * @param method the http method
     * @return the OpenTelemetry span
     * @throws IllegalArgumentException if the method is not supported
     */
    public static Span getClientNamingHttpSpan(String method) throws IllegalArgumentException {
        switch (method) {
            case HttpMethod.GET:
                return tracer.spanBuilder("nacos.client.naming.http.get").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case HttpMethod.POST:
                return tracer.spanBuilder("nacos.client.naming.http.post").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case HttpMethod.DELETE:
                return tracer.spanBuilder("nacos.client.naming.http.delete").setSpanKind(SpanKind.CLIENT)
                        .setAttribute("nacos.client.version", VersionUtils.getFullClientVersion()).startSpan();
            case HttpMethod.PUT:
                return tracer.spanBuilder("nacos.client.naming.http.put").setSpanKind(SpanKind.CLIENT)
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
        return tracer;
    }
    
    /**
     * Set the Nacos client OpenTelemetry tracer.
     *
     * @param openTelemetry the OpenTelemetry instance
     */
    public static Tracer setTracer(OpenTelemetry openTelemetry) {
        return setTracer(openTelemetry, "Nacos-Java-Client", VersionUtils.version);
    }
    
    public static Tracer setTracer(OpenTelemetry openTelemetry, String name, String version) {
        tracer = openTelemetry.getTracer(name, version);
        return tracer;
    }
}
