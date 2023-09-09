/*
 *
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 *
 */

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
        // set your own nacos server address
        properties.put("serverAddr", "1.1.1.1:8848");
        nacosConfigService = new NacosConfigService(properties);
    }
    
    @After
    public void clean() {
        LocalConfigInfoProcessor.cleanAllSnapshot();
    }
    
    @Test
    public void testConfigJaeger() throws NacosException {
        final String dataId = "1";
        final String group = "2";
        final int timeout = 3000;
        Span testSpan = TraceMonitor.getTracer().spanBuilder("nacos.client.config.test").startSpan();
        String config;
        boolean b;
        try (Scope ignored = testSpan.makeCurrent()) {
            config = nacosConfigService.getConfig(dataId, group, timeout);
            b = nacosConfigService.removeConfig(dataId, group);
        } finally {
            testSpan.end();
        }
        Assert.assertNull(config);
        Assert.assertTrue(b);
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
