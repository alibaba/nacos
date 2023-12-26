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

package com.alibaba.nacos.client.monitor.delegate;

import com.alibaba.nacos.client.monitor.TraceMonitor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.After;
import org.junit.BeforeClass;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class OpenTelemetryBaseTest {
    
    public static OpenTelemetry testOpenTelemetry = null;
    
    public static OpenTelemetryBaseTest.TestExporter testExporter = null;
    
    @BeforeClass
    public static void init() {
        testExporter = new OpenTelemetryBaseTest.TestExporter();
        testOpenTelemetry = OpenTelemetrySdk.builder().setTracerProvider(
                SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(testExporter)).build()).build();
        TraceMonitor.setOpenTelemetry(testOpenTelemetry);
    }
    
    @After
    public void clear() {
        testExporter.exportedSpans.clear();
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
