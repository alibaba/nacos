package com.alibaba.nacos.client.monitor;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.NacosConfigService;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ConfigTraceToJaegerTest {
    
    private static final String JAEGER_ENDPOINT = "http://localhost:4317";
    
    @BeforeClass
    public static void init() {
        TraceMonitor.setTracer(initOpenTelemetry());
    }
    
    private NacosConfigService nacosConfigService;
    
    @Before
    public void mock() throws Exception {
        final Properties properties = new Properties();
        properties.put("serverAddr", "1.1.1.1");
        nacosConfigService = new NacosConfigService(properties);
    }
    
    @After
    public void clean() {
        LocalConfigInfoProcessor.cleanAllSnapshot();
    }
    
    @Test
    public void testGetConfig() throws NacosException {
        final String dataId = "1";
        final String group = "2";
        final int timeout = 3000;
        Span testSpan = TraceMonitor.getTracer().spanBuilder("nacos.client.config.test").startSpan();
        String config;
        try (Scope ignored = testSpan.makeCurrent()) {
            config = nacosConfigService.getConfig(dataId, group, timeout);
        } finally {
            testSpan.end();
        }
        Assert.assertNull(config);
    }
    
    /**
     * Initialize an OpenTelemetry SDK with a {@link OtlpGrpcSpanExporter} and a {@link BatchSpanProcessor}.
     *
     * @return A ready-to-use {@link OpenTelemetry} instance.
     */
    static OpenTelemetry initOpenTelemetry() {
        // Export traces to Jaeger over OTLP
        OtlpGrpcSpanExporter jaegerOtlpExporter = OtlpGrpcSpanExporter.builder().setEndpoint(JAEGER_ENDPOINT)
                .setTimeout(30, TimeUnit.SECONDS).build();
        
        Resource serviceNameResource = Resource.create(
                Attributes.of(ResourceAttributes.SERVICE_NAME, "nacos-otel-jaeger-example"));
        
        // Set to process the spans by the Jaeger Exporter
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(jaegerOtlpExporter).build())
                .setResource(Resource.getDefault().merge(serviceNameResource)).build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        
        // it's always a good idea to shut down the SDK cleanly at JVM exit.
        Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::close));
        
        return openTelemetry;
    }
}
