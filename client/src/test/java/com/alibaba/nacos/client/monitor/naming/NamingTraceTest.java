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

package com.alibaba.nacos.client.monitor.naming;

import com.alibaba.nacos.client.monitor.TraceMonitor;
import com.alibaba.nacos.common.utils.VersionUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class NamingTraceTest {
    
    public static Tracer testTracer = null;
    
    public static OpenTelemetry testOpenTelemetry = null;
    
    public static NamingTraceTest.TestExporter testExporter = null;
    
    @BeforeClass
    public static void init() {
        testExporter = new NamingTraceTest.TestExporter();
        testOpenTelemetry = OpenTelemetrySdk.builder().setTracerProvider(
                SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(testExporter)).build()).build();
        testTracer = TraceMonitor.setTracer(testOpenTelemetry);
    }
    
    @After
    public void clear() {
        testExporter.exportedSpans.clear();
    }
    
    @Test
    public void testGetClientNamingRpcSpan() {
        Span span = NamingTrace.getClientNamingRpcSpan("GRPC");
        AttributeKey<String> testKey = AttributeKey.stringKey("test.key");
        AttributeKey<String> versionKey = AttributeKey.stringKey("nacos.client.version");
        runSpan(span);
        
        for (SpanData spanData : testExporter.exportedSpans) {
            Attributes attributes = spanData.getAttributes();
            
            Assert.assertEquals(attributes.get(testKey), "test.value");
            Assert.assertEquals(attributes.get(versionKey), VersionUtils.getFullClientVersion());
            Assert.assertEquals(spanData.getName(), "nacos.client.naming.rpc.GRPC");
        }
    }
    
    @Test
    public void testGetClientNamingHttpSpan() {
        Span span = NamingTrace.getClientNamingHttpSpan("GET");
        AttributeKey<String> testKey = AttributeKey.stringKey("test.key");
        AttributeKey<String> versionKey = AttributeKey.stringKey("nacos.client.version");
        runSpan(span);
        
        for (SpanData spanData : testExporter.exportedSpans) {
            Attributes attributes = spanData.getAttributes();
            
            Assert.assertEquals(attributes.get(testKey), "test.value");
            Assert.assertEquals(attributes.get(versionKey), VersionUtils.getFullClientVersion());
            Assert.assertEquals(spanData.getName(), "nacos.client.naming.http.GET");
        }
    }
    
    @Test
    public void testGetClientNamingServiceSpan() {
        Span span = NamingTrace.getClientNamingServiceSpan("test");
        AttributeKey<String> testKey = AttributeKey.stringKey("test.key");
        AttributeKey<String> versionKey = AttributeKey.stringKey("nacos.client.version");
        runSpan(span);
        
        for (SpanData spanData : testExporter.exportedSpans) {
            Attributes attributes = spanData.getAttributes();
            
            Assert.assertEquals(attributes.get(testKey), "test.value");
            Assert.assertEquals(attributes.get(versionKey), VersionUtils.getFullClientVersion());
            Assert.assertEquals(spanData.getName(), "nacos.client.naming.service.test");
        }
    }
    
    @Test
    public void testGetClientNamingWorkerSpan() {
        Span span = NamingTrace.getClientNamingWorkerSpan("test");
        AttributeKey<String> testKey = AttributeKey.stringKey("test.key");
        AttributeKey<String> versionKey = AttributeKey.stringKey("nacos.client.version");
        runSpan(span);
        
        for (SpanData spanData : testExporter.exportedSpans) {
            Attributes attributes = spanData.getAttributes();
            
            Assert.assertEquals(attributes.get(testKey), "test.value");
            Assert.assertEquals(attributes.get(versionKey), VersionUtils.getFullClientVersion());
            Assert.assertEquals(spanData.getName(), "nacos.client.naming.worker.test");
        }
    }
    
    private void runSpan(Span span) {
        try (Scope ignored = span.makeCurrent()) {
            span.setStatus(StatusCode.OK);
            span.setAttribute("test.key", "test.value");
        } finally {
            span.end();
        }
    }
    
    public static class TestExporter implements SpanExporter {
        
        public final List<SpanData> exportedSpans = Collections.synchronizedList(new ArrayList<>());
        
        @ParametersAreNonnullByDefault
        @Override
        public CompletableResultCode export(Collection<SpanData> collection) {
            exportedSpans.addAll(collection);
            return CompletableResultCode.ofSuccess();
        }
        
        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }
        
        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
