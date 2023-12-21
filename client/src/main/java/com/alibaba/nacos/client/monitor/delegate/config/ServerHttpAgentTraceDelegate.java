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

package com.alibaba.nacos.client.monitor.delegate.config;

import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.monitor.config.ConfigTrace;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.Map;

/**
 * Wrap ServerHttpAgent for tracing.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class ServerHttpAgentTraceDelegate extends ServerHttpAgent {
    
    public ServerHttpAgentTraceDelegate(ServerListManager mgr) {
        super(mgr);
    }
    
    private SpanBuilder initHttpSpanBuilder(String methodName, String httpMethod) {
        SpanBuilder spanBuilder = ConfigTrace.getClientConfigHttpSpanBuilder(httpMethod);
        spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, super.getClass().getName());
        spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
        spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, super.getName());
        spanBuilder.setAttribute(NacosSemanticAttributes.TENANT, super.getTenant());
        spanBuilder.setAttribute(NacosSemanticAttributes.NAMESPACE, super.getNamespace());
        spanBuilder.setAttribute(NacosSemanticAttributes.ENCODE, super.getEncode());
        return spanBuilder;
    }
    
    @Override
    public HttpRestResult<String> httpGet(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        String methodName = "httpGet";
        String httpMethod = HttpMethod.GET;
        Span span = initHttpSpanBuilder(methodName, httpMethod).startSpan();
        
        try (Scope ignored = span.makeCurrent()) {
            if (span.isRecording()) {
                // No providing span context for http request since HTTP agent will be deprecated.
                span.setAttribute(SemanticAttributes.HTTP_METHOD, httpMethod);
                span.setAttribute(SemanticAttributes.HTTP_URL, path);
            }
            
            HttpRestResult<String> restResult = super.httpGet(path, headers, paramValues, encode, readTimeoutMs);
            
            if (span.isRecording()) {
                int resultCode = restResult.getCode();
                span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, resultCode);
                
                if (isFail(restResult)) {
                    span.setStatus(StatusCode.ERROR, "Http request failed");
                } else {
                    span.setStatus(StatusCode.OK, "Http request success");
                }
            }
            return restResult;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    @Override
    public HttpRestResult<String> httpPost(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        String methodName = "httpPost";
        String httpMethod = HttpMethod.POST;
        Span span = initHttpSpanBuilder(methodName, httpMethod).startSpan();
        
        try (Scope ignored = span.makeCurrent()) {
            if (span.isRecording()) {
                // No providing span context for http request since HTTP agent will be deprecated.
                span.setAttribute(SemanticAttributes.HTTP_METHOD, httpMethod);
                span.setAttribute(SemanticAttributes.HTTP_URL, path);
            }
            
            HttpRestResult<String> restResult = super.httpPost(path, headers, paramValues, encode, readTimeoutMs);
            
            if (span.isRecording()) {
                int resultCode = restResult.getCode();
                span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, resultCode);
                
                if (isFail(restResult)) {
                    span.setStatus(StatusCode.ERROR, "Http request failed");
                } else {
                    span.setStatus(StatusCode.OK, "Http request success");
                }
            }
            return restResult;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    @Override
    public HttpRestResult<String> httpDelete(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        String methodName = "httpDelete";
        String httpMethod = HttpMethod.DELETE;
        Span span = initHttpSpanBuilder(methodName, httpMethod).startSpan();
        
        try (Scope ignored = span.makeCurrent()) {
            if (span.isRecording()) {
                // No providing span context for http request since HTTP agent will be deprecated.
                span.setAttribute(SemanticAttributes.HTTP_METHOD, httpMethod);
                span.setAttribute(SemanticAttributes.HTTP_URL, path);
            }
            
            HttpRestResult<String> restResult = super.httpDelete(path, headers, paramValues, encode, readTimeoutMs);
            
            if (span.isRecording()) {
                int resultCode = restResult.getCode();
                span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, resultCode);
                
                if (isFail(restResult)) {
                    span.setStatus(StatusCode.ERROR, "Http request failed");
                } else {
                    span.setStatus(StatusCode.OK, "Http request success");
                }
            }
            return restResult;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
}
