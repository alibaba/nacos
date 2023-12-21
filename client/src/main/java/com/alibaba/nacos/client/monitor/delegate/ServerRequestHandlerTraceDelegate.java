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

import com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.client.monitor.TraceMonitor;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.utils.VersionUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

/**
 * Wrap ServerRequestHandler for tracing.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class ServerRequestHandlerTraceDelegate {
    
    /**
     * Wrap ServerRequestHandler for tracing.
     *
     * @param handler ServerRequestHandler
     * @return ServerRequestHandler
     */
    public static ServerRequestHandler warp(ServerRequestHandler handler) {
        return (request) -> {
            String methodName = "requestReply";
            String moduleName = request.getModule();
            
            String spanName = TraceMonitor.getNacosClientRequestFromServerSpanName() + " / " + moduleName;
            SpanBuilder spanBuilder = TraceMonitor.getTracer().spanBuilder(spanName);
            // SpanKind.SERVER means incoming span rather than the span in the server side.
            // See https://opentelemetry.io/docs/specs/otel/trace/api/#spankind
            spanBuilder.setSpanKind(SpanKind.SERVER);
            spanBuilder.setAttribute(NacosSemanticAttributes.CLIENT_VERSION, VersionUtils.getFullClientVersion());
            spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, handler.getClass().getName());
            spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
            spanBuilder.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_ID, request.getRequestId());
            spanBuilder.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_MODULE, moduleName);
            
            Span span = spanBuilder.startSpan();
            try (Scope ignored = span.makeCurrent()) {
                
                if (span.isRecording()) {
                    if (request instanceof ConfigChangeNotifyRequest) {
                        ConfigChangeNotifyRequest configChangeNotifyRequest = (ConfigChangeNotifyRequest) request;
                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_ID,
                                configChangeNotifyRequest.getRequestId());
                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_DATA_ID,
                                configChangeNotifyRequest.getDataId());
                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_GROUP,
                                configChangeNotifyRequest.getGroup());
                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_TENANT,
                                configChangeNotifyRequest.getTenant());
                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_TYPE,
                                ConfigChangeNotifyRequest.class.getSimpleName());
                        
                    } else if (request instanceof ClientConfigMetricRequest) {
                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_TYPE,
                                ClientConfigMetricRequest.class.getSimpleName());
                        
                    } else if (request instanceof NotifySubscriberRequest) {
                        NotifySubscriberRequest notifySubscriberRequest = (NotifySubscriberRequest) request;
                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_NAMESPACE,
                                notifySubscriberRequest.getNamespace());
                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_SERVICE_NAME,
                                notifySubscriberRequest.getServiceName());
                        if (notifySubscriberRequest.getServiceInfo() != null) {
                            span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_GROUP,
                                    notifySubscriberRequest.getServiceInfo().getGroupName());
                            span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_SERVICE_CLUSTER_NAME,
                                    notifySubscriberRequest.getServiceInfo().getClusters());
                        }
                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_TYPE,
                                NotifySubscriberRequest.class.getSimpleName());
                        
                    }
                    // More request type can be added here.
                }
                
                return handler.requestReply(request);
                
            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
                throw e;
            } finally {
                span.end();
            }
            
        };
    }
}
