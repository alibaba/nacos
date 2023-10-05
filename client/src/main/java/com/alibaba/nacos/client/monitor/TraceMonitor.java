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

import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.VersionUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Map;

/**
 * Unified management of OpenTelemetry tracer.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class TraceMonitor {
    
    private static final String NACOS_CLIENT_NAME = "Nacos-Java-Client";
    
    private static final String NACOS_CLIENT_REQUEST_FROM_SERVER_SPAN = "Nacos.client.request.from.server";
    
    private static OpenTelemetry nacosOpenTelemetry = GlobalOpenTelemetry.get();
    
    private static final TextMapSetter<Header> HTTP_CONTEXT_SETTER = (carrier, key, value) -> {
        assert carrier != null;
        carrier.addParam(key, value);
    };
    
    private static final TextMapSetter<Map<String, String>> RPC_CONTEXT_SETTER = (carrier, key, value) -> {
        assert carrier != null;
        carrier.put(key, value);
    };
    
    public static TextMapSetter<Header> getHttpContextSetter() {
        return HTTP_CONTEXT_SETTER;
    }
    
    public static TextMapSetter<Map<String, String>> getRpcContextSetter() {
        return RPC_CONTEXT_SETTER;
    }
    
    /**
     * Get the Nacos client OpenTelemetry tracer. We should call <b>getTracer()</b> in
     * {@link io.opentelemetry.api.OpenTelemetry} instance rather than return a
     * {@link io.opentelemetry.api.trace.Tracer} directly.
     *
     * <p>See <a href="https://opentelemetry.io/docs/instrumentation/java/manual/#acquiring-a-tracer">OpenTelemetry
     * doc</a>
     *
     * @return the OpenTelemetry tracer
     */
    public static Tracer getTracer() {
        return nacosOpenTelemetry.getTracer(NACOS_CLIENT_NAME, VersionUtils.version);
    }
    
    /**
     * Get the Nacos client OpenTelemetry instance.
     *
     * @return the OpenTelemetry instance
     */
    public static OpenTelemetry getOpenTelemetry() {
        return nacosOpenTelemetry;
    }
    
    /**
     * Set the Nacos client OpenTelemetry instance.
     *
     * @param openTelemetry the OpenTelemetry instance
     */
    public static void setOpenTelemetry(OpenTelemetry openTelemetry) {
        nacosOpenTelemetry = openTelemetry;
    }
    
    public static String getNacosClientRequestFromServerSpanName() {
        return NACOS_CLIENT_REQUEST_FROM_SERVER_SPAN;
    }
    
    /**
     * Reset the Nacos client OpenTelemetry instance to global.
     */
    public static void resetOpenTelemetry() {
        nacosOpenTelemetry = GlobalOpenTelemetry.get();
    }
}
