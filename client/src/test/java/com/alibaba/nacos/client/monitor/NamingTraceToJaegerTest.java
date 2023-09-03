package com.alibaba.nacos.client.monitor;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.alibaba.nacos.client.naming.NacosNamingService;
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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class NamingTraceToJaegerTest {
    
    private static final String JAEGER_ENDPOINT = "http://localhost:4317";
    
    @BeforeClass
    public static void init() {
        TraceMonitor.setTracer(initOpenTelemetry());
    }
    
    private NacosNamingService client;
    
    @Before
    public void mock() throws Exception {
        Properties prop = new Properties();
        prop.setProperty("serverAddr", "10.3.242.223:8848");
        prop.put(PropertyKeyConst.NAMESPACE, "test");
        client = new NacosNamingService(prop);
    }
    
    @After
    public void clean() {
        LocalConfigInfoProcessor.cleanAllSnapshot();
    }
    
    @Test
    public void testNamingJaeger() throws NacosException {
        //given
        String serviceName = "service1";
        String ip = "1.1.1.1";
        int port = 10000;
        //when
        Span testSpan = TraceMonitor.getTracer().spanBuilder("nacos.client.naming.test").startSpan();
        try (Scope ignored = testSpan.makeCurrent()) {
            client.registerInstance(serviceName, ip, port);
            client.selectInstances(serviceName, true, false);
        } finally {
            testSpan.end();
        }
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
