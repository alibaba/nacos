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

import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.monitor.config.ClientWorkerTraceProxy;
import com.alibaba.nacos.client.monitor.config.ConfigTrace;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * Utils for dynamic proxy to the OpenTelemetry tracing.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class TraceDynamicProxy {
    
    public static ClientWorkerTraceProxy getClientWorkerTraceProxy(ClientWorker clientWorker) {
        return (ClientWorkerTraceProxy) Proxy.newProxyInstance(TraceDynamicProxy.class.getClassLoader(),
                new Class[] {ClientWorkerTraceProxy.class}, (proxy, method, args) -> {
                    
                    SpanBuilder spanBuilder = ConfigTrace.getClientConfigServiceSpanBuilder(method.getName());
                    spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, clientWorker.getClass().getName());
                    spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, method.getName());
                    spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, clientWorker.getAgentName());
                    
                    // Service level config span
                    switch (method.getName()) {
                        case "addTenantListenersWithContent": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.DATA_ID, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    span.setAttribute(NacosSemanticAttributes.CONTENT, (String) args[2]);
                                }
                                
                                result = method.invoke(clientWorker, args);
                                
                            } catch (InvocationTargetException e) {
                                Throwable targetException = e.getTargetException();
                                span.recordException(targetException);
                                span.setStatus(StatusCode.ERROR, targetException.getClass().getSimpleName());
                                throw targetException;
                            } finally {
                                span.end();
                            }
                            
                            return result;
                        }
                        case "addTenantListeners":
                        case "removeTenantListener": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.DATA_ID, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                }
                                
                                result = method.invoke(clientWorker, args);
                                
                            } catch (InvocationTargetException e) {
                                Throwable targetException = e.getTargetException();
                                span.recordException(targetException);
                                span.setStatus(StatusCode.ERROR, targetException.getClass().getSimpleName());
                                throw targetException;
                            } finally {
                                span.end();
                            }
                            
                            return result;
                        }
                        case "getServerConfig": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.DATA_ID, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    span.setAttribute(NacosSemanticAttributes.TENANT, (String) args[2]);
                                    span.setAttribute(NacosSemanticAttributes.TIMEOUT_MS, (long) args[3]);
                                }
                                
                                result = method.invoke(clientWorker, args);
                                
                                if (span.isRecording() && result != null) {
                                    ConfigResponse configResponse = (ConfigResponse) result;
                                    span.setAttribute(NacosSemanticAttributes.CONTENT, configResponse.getContent());
                                }
                                
                            } catch (InvocationTargetException e) {
                                Throwable targetException = e.getTargetException();
                                span.recordException(targetException);
                                span.setStatus(StatusCode.ERROR, targetException.getClass().getSimpleName());
                                throw targetException;
                            } finally {
                                span.end();
                            }
                            
                            return result;
                        }
                        case "removeConfig": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.DATA_ID, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    span.setAttribute(NacosSemanticAttributes.TENANT, (String) args[2]);
                                    span.setAttribute(NacosSemanticAttributes.TAG, (String) args[3]);
                                }
                                
                                result = method.invoke(clientWorker, args);
                                
                                boolean removeResult = (boolean) result;
                                if (removeResult) {
                                    span.setStatus(StatusCode.OK, "remove config success");
                                } else {
                                    span.setStatus(StatusCode.ERROR, "remove config failed");
                                }
                                
                            } catch (InvocationTargetException e) {
                                Throwable targetException = e.getTargetException();
                                span.recordException(targetException);
                                span.setStatus(StatusCode.ERROR, targetException.getClass().getSimpleName());
                                throw targetException;
                            } finally {
                                span.end();
                            }
                            
                            return result;
                        }
                        case "publishConfig": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.DATA_ID, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    span.setAttribute(NacosSemanticAttributes.TENANT, (String) args[2]);
                                    span.setAttribute(NacosSemanticAttributes.APPLICATION_NAME, (String) args[3]);
                                    span.setAttribute(NacosSemanticAttributes.TAG, (String) args[4]);
                                    span.setAttribute(NacosSemanticAttributes.CONTENT, (String) args[6]);
                                    span.setAttribute(NacosSemanticAttributes.CONFIG_TYPE, (String) args[9]);
                                }
                                
                                result = method.invoke(clientWorker, args);
                                
                                boolean publishResult = (boolean) result;
                                if (publishResult) {
                                    span.setStatus(StatusCode.OK, "publish config success");
                                } else {
                                    span.setStatus(StatusCode.ERROR, "publish config failed");
                                }
                                
                            } catch (InvocationTargetException e) {
                                Throwable targetException = e.getTargetException();
                                span.recordException(targetException);
                                span.setStatus(StatusCode.ERROR, targetException.getClass().getSimpleName());
                                throw targetException;
                            } finally {
                                span.end();
                            }
                            
                            return result;
                        }
                        case "isHealthServer": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                result = method.invoke(clientWorker, args);
                                
                                boolean isHealthServer = (boolean) result;
                                if (isHealthServer) {
                                    span.setStatus(StatusCode.OK, "Server is up");
                                } else {
                                    span.setStatus(StatusCode.ERROR, "Server is down");
                                }
                                
                            } catch (InvocationTargetException e) {
                                Throwable targetException = e.getTargetException();
                                span.recordException(targetException);
                                span.setStatus(StatusCode.ERROR, targetException.getClass().getSimpleName());
                                throw targetException;
                            } finally {
                                span.end();
                            }
                            
                            return result;
                        }
                        default:
                            break;
                    }
                    
                    spanBuilder = ConfigTrace.getClientConfigWorkerSpanBuilder(method.getName());
                    spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, clientWorker.getClass().getName());
                    spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, method.getName());
                    spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, clientWorker.getAgentName());
                    
                    // Worker level config span
                    switch (method.getName()) {
                        case "addCacheDataIfAbsent":
                        case "getCache": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.DATA_ID, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    String tenant =
                                            args.length > 2 ? (String) args[2] : clientWorker.getAgent().getTenant();
                                    span.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                                }
                                
                                result = method.invoke(clientWorker, args);
                                
                            } catch (InvocationTargetException e) {
                                Throwable targetException = e.getTargetException();
                                span.recordException(targetException);
                                span.setStatus(StatusCode.ERROR, targetException.getClass().getSimpleName());
                                throw targetException;
                            } finally {
                                span.end();
                            }
                            
                            return result;
                        }
                        default:
                            break;
                    }
                    
                    try {
                        return method.invoke(clientWorker, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                    
                });
    }
    
}
