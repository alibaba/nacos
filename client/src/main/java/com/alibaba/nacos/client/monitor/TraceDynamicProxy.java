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

import com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.naming.remote.response.QueryServiceResponse;
import com.alibaba.nacos.api.naming.remote.response.ServiceListResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.impl.CacheData;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.proxy.ClientWorkerProxy;
import com.alibaba.nacos.client.monitor.config.ConfigTrace;
import com.alibaba.nacos.client.monitor.naming.NamingGrpcRedoServiceTraceProxy;
import com.alibaba.nacos.client.monitor.naming.NamingTrace;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.redo.NamingGrpcRedoService;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.InstanceRedoData;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.util.List;

/**
 * Utils for dynamic proxy to the OpenTelemetry tracing.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class TraceDynamicProxy {
    
    // Config module
    
    // TODO: Remove this method
    public static ClientWorkerProxy getClientWorkerTraceProxy(ClientWorker clientWorker) {
        return (ClientWorkerProxy) Proxy.newProxyInstance(TraceDynamicProxy.class.getClassLoader(),
                new Class[] {ClientWorkerProxy.class}, (proxy, method, args) -> {
                    String methodName = method.getName();
                    
                    SpanBuilder spanBuilder = ConfigTrace.getClientConfigServiceSpanBuilder(methodName);
                    spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, clientWorker.getClass().getName());
                    spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
                    spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, clientWorker.getAgentName());
                    
                    // Service level config span
                    switch (methodName) {
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
                                    span.setAttribute(NacosSemanticAttributes.NOTIFY, (boolean) args[4]);
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
                                
                                if (span.isRecording() && result != null) {
                                    boolean removeResult = (boolean) result;
                                    if (removeResult) {
                                        span.setStatus(StatusCode.OK, "Remove config success");
                                    } else {
                                        span.setStatus(StatusCode.ERROR, "Remove config failed");
                                    }
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
                                
                                if (span.isRecording() && result != null) {
                                    boolean publishResult = (boolean) result;
                                    if (publishResult) {
                                        span.setStatus(StatusCode.OK, "Publish config success");
                                    } else {
                                        span.setStatus(StatusCode.ERROR, "Publish config failed");
                                    }
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
                                
                                if (span.isRecording() && result != null) {
                                    boolean isHealthServer = (boolean) result;
                                    if (isHealthServer) {
                                        span.setStatus(StatusCode.OK, "Server is up");
                                    } else {
                                        span.setStatus(StatusCode.ERROR, "Server is down");
                                    }
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
                    
                    String targetMethodName = "addCacheDataIfAbsent";
                    if (targetMethodName.equals(methodName)) {
                        Object result;
                        
                        spanBuilder = ConfigTrace.getClientConfigWorkerSpanBuilder(methodName);
                        spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, clientWorker.getClass().getName());
                        spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
                        spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, clientWorker.getAgentName());
                        
                        Span span = spanBuilder.startSpan();
                        try (Scope ignored = span.makeCurrent()) {
                            
                            if (span.isRecording()) {
                                span.setAttribute(NacosSemanticAttributes.DATA_ID, (String) args[0]);
                                span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                String tenant = args.length > 2 ? (String) args[2] : clientWorker.getAgentTenant();
                                span.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                            }
                            
                            result = method.invoke(clientWorker, args);
                            
                            if (span.isRecording() && result instanceof CacheData) {
                                CacheData cacheData = (CacheData) result;
                                span.setAttribute(NacosSemanticAttributes.CONTENT, cacheData.getContent());
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
                    
                    try {
                        return method.invoke(clientWorker, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                    
                });
    }
    
    public static ServerRequestHandler getServerRequestHandlerTraceProxy(ServerRequestHandler serverRequestHandler) {
        return (ServerRequestHandler) Proxy.newProxyInstance(TraceDynamicProxy.class.getClassLoader(),
                new Class[] {ServerRequestHandler.class}, (proxy, method, args) -> {
                    String methodName = method.getName();
                    
                    String requestReplyMethodName = "requestReply";
                    if (requestReplyMethodName.equals(methodName) && args[0] != null) {
                        
                        Object result;
                        Request request = (Request) args[0];
                        String moduleName = request.getModule();
                        
                        String spanName = TraceMonitor.getNacosClientRequestFromServerSpanName() + " / " + moduleName;
                        SpanBuilder spanBuilder = TraceMonitor.getTracer().spanBuilder(spanName);
                        
                        // SpanKind.SERVER means incoming span rather than the span in the server side.
                        // See https://opentelemetry.io/docs/specs/otel/trace/api/#spankind
                        spanBuilder.setSpanKind(SpanKind.SERVER);
                        spanBuilder.setAttribute(NacosSemanticAttributes.CLIENT_VERSION,
                                VersionUtils.getFullClientVersion());
                        spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE,
                                serverRequestHandler.getClass().getName());
                        spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
                        spanBuilder.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_ID,
                                request.getRequestId());
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
                                        span.setAttribute(
                                                NacosSemanticAttributes.RequestAttributes.REQUEST_SERVICE_CLUSTER_NAME,
                                                notifySubscriberRequest.getServiceInfo().getClusters());
                                    }
                                    span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_TYPE,
                                            NotifySubscriberRequest.class.getSimpleName());
                                    
                                }
                            }
                            
                            result = method.invoke(serverRequestHandler, args);
                            
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
                    
                    try {
                        return method.invoke(serverRequestHandler, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                });
    }
    
    public static HttpAgent getHttpAgentTraceProxy(HttpAgent httpAgent) {
        return (HttpAgent) Proxy.newProxyInstance(TraceDynamicProxy.class.getClassLoader(),
                new Class[] {HttpAgent.class}, (proxy, method, args) -> {
                    String methodName = method.getName();
                    String httpMethod;
                    
                    switch (methodName) {
                        case "httpGet":
                            httpMethod = HttpMethod.GET;
                            break;
                        case "httpPost":
                            httpMethod = HttpMethod.POST;
                            break;
                        case "httpDelete":
                            httpMethod = HttpMethod.DELETE;
                            break;
                        default:
                            try {
                                return method.invoke(httpAgent, args);
                            } catch (InvocationTargetException e) {
                                throw e.getTargetException();
                            }
                    }
                    
                    SpanBuilder spanBuilder = ConfigTrace.getClientConfigHttpSpanBuilder(httpMethod);
                    spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, httpAgent.getClass().getName());
                    spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
                    spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, httpAgent.getName());
                    spanBuilder.setAttribute(NacosSemanticAttributes.TENANT, httpAgent.getTenant());
                    spanBuilder.setAttribute(NacosSemanticAttributes.NAMESPACE, httpAgent.getNamespace());
                    spanBuilder.setAttribute(NacosSemanticAttributes.ENCODE, httpAgent.getEncode());
                    
                    Object result;
                    Span span = spanBuilder.startSpan();
                    try (Scope ignored = span.makeCurrent()) {
                        if (span.isRecording()) {
                            // No providing span context for http request since HTTP agent will be deprecated.
                            span.setAttribute(SemanticAttributes.HTTP_METHOD, httpMethod);
                            span.setAttribute(SemanticAttributes.HTTP_URL, (String) args[0]);
                        }
                        
                        result = method.invoke(httpAgent, args);
                        
                        if (span.isRecording() && result instanceof HttpRestResult) {
                            HttpRestResult<?> restResult = (HttpRestResult<?>) result;
                            int resultCode = restResult.getCode();
                            span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, resultCode);
                            
                            if (isFail(resultCode)) {
                                span.setStatus(StatusCode.ERROR, "Http request failed");
                            } else {
                                span.setStatus(StatusCode.OK, "Http request success");
                            }
                            
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
                    
                });
    }
    
    // Naming module
    
    public static NamingClientProxy getNamingClientProxyTraceProxy(NamingClientProxy namingClientProxy) {
        return (NamingClientProxy) Proxy.newProxyInstance(TraceDynamicProxy.class.getClassLoader(),
                new Class[] {NamingClientProxy.class}, (proxy, method, args) -> {
                    String methodName = method.getName();
                    String namingClientType;
                    SpanBuilder spanBuilder;
                    if (namingClientProxy instanceof NamingGrpcClientProxy) {
                        namingClientType = "grpc";
                        spanBuilder = NamingTrace.getClientNamingWorkerSpanBuilder(methodName);
                    } else if (namingClientProxy instanceof NamingHttpClientProxy) {
                        namingClientType = "http";
                        spanBuilder = NamingTrace.getClientNamingWorkerSpanBuilder(methodName);
                    } else {
                        namingClientType = "Delegate";
                        spanBuilder = NamingTrace.getClientNamingServiceSpanBuilder(methodName);
                    }
                    
                    spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, namingClientProxy.getClass().getName());
                    spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
                    spanBuilder.setAttribute(NacosSemanticAttributes.NAMESPACE, namingClientProxy.getNamespace());
                    spanBuilder.setAttribute(NacosSemanticAttributes.NAMING_CLIENT_TYPE, namingClientType);
                    
                    switch (methodName) {
                        case "registerService":
                        case "deregisterService": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    Instance instance = (Instance) args[2];
                                    span.setAttribute(NacosSemanticAttributes.INSTANCE, instance.toString());
                                }
                                
                                result = method.invoke(namingClientProxy, args);
                                
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
                        case "batchRegisterService":
                        case "batchDeregisterService": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    List<?> instanceList = (List<?>) args[2];
                                    span.setAttribute(NacosSemanticAttributes.INSTANCE,
                                            StringUtils.join(instanceList, ", "));
                                }
                                
                                result = method.invoke(namingClientProxy, args);
                                
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
                        case "updateInstance": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.PUT);
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    Instance instance = (Instance) args[2];
                                    span.setAttribute(NacosSemanticAttributes.INSTANCE, instance.toString());
                                }
                                
                                result = method.invoke(namingClientProxy, args);
                                
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
                        case "queryInstancesOfService": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    span.setAttribute(NacosSemanticAttributes.CLUSTER, (String) args[2]);
                                    span.setAttribute(NacosSemanticAttributes.UDP_PORT, (int) args[3]);
                                    span.setAttribute(NacosSemanticAttributes.HEALTHY_ONLY, (boolean) args[4]);
                                }
                                
                                result = method.invoke(namingClientProxy, args);
                                
                                if (span.isRecording() && result != null) {
                                    if (result instanceof QueryServiceResponse) {
                                        QueryServiceResponse response = ((QueryServiceResponse) result);
                                        if (response.getServiceInfo() != null) {
                                            span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_RESULT,
                                                    StringUtils.join(response.getServiceInfo().getHosts(), ", "));
                                        }
                                    } else if (result instanceof String) {
                                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_RESULT,
                                                (String) result);
                                    }
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
                        case "queryService": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.GET);
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                }
                                
                                result = method.invoke(namingClientProxy, args);
                                
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
                        case "createService": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    Service service = (Service) args[0];
                                    span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.POST);
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, service.getName());
                                    span.setAttribute(NacosSemanticAttributes.GROUP, service.getGroupName());
                                }
                                
                                result = method.invoke(namingClientProxy, args);
                                
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
                        case "deleteService": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.DELETE);
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                }
                                
                                result = method.invoke(namingClientProxy, args);
                                
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
                        case "updateService": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    Service service = (Service) args[0];
                                    span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.PUT);
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, service.getName());
                                    span.setAttribute(NacosSemanticAttributes.GROUP, service.getGroupName());
                                }
                                
                                result = method.invoke(namingClientProxy, args);
                                
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
                        case "getServiceList": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    switch (namingClientType) {
                                        case "grpc":
                                            span.setAttribute(SemanticAttributes.RPC_SYSTEM, "grpc");
                                            break;
                                        case "http":
                                            span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.GET);
                                            break;
                                        default:
                                            break;
                                    }
                                    span.setAttribute(NacosSemanticAttributes.PAGE_NO, (int) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.PAGE_SIZE, (int) args[1]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[2]);
                                }
                                
                                result = method.invoke(namingClientProxy, args);
                                
                                if (span.isRecording() && result != null) {
                                    if (result instanceof ServiceListResponse) {
                                        ServiceListResponse response = ((ServiceListResponse) result);
                                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_RESULT,
                                                StringUtils.join(response.getServiceNames(), ", "));
                                    } else if (result instanceof String) {
                                        span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_RESULT,
                                                (String) result);
                                    }
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
                        case "subscribe":
                        case "unsubscribe":
                        case "isSubscribed": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    span.setAttribute(NacosSemanticAttributes.CLUSTER, (String) args[2]);
                                }
                                
                                result = method.invoke(namingClientProxy, args);
                                
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
                        case "serverHealthy": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                result = method.invoke(namingClientProxy, args);
                                
                                if (span.isRecording() && result != null) {
                                    boolean isHealthy = (boolean) result;
                                    if (isHealthy) {
                                        span.setStatus(StatusCode.OK, "Server is up");
                                    } else {
                                        span.setStatus(StatusCode.ERROR, "Server is down");
                                    }
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
                    
                    try {
                        return method.invoke(namingClientProxy, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                });
    }
    
    public static NamingGrpcRedoServiceTraceProxy getNamingGrpcRedoServiceTraceProxy(
            NamingGrpcRedoService namingGrpcRedoService) {
        return (NamingGrpcRedoServiceTraceProxy) Proxy.newProxyInstance(TraceDynamicProxy.class.getClassLoader(),
                new Class[] {NamingGrpcRedoServiceTraceProxy.class}, (proxy, method, args) -> {
                    String methodName = method.getName();
                    
                    SpanBuilder spanBuilder = NamingTrace.getClientNamingWorkerSpanBuilder(methodName);
                    spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE,
                            namingGrpcRedoService.getClass().getName());
                    spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
                    spanBuilder.setAttribute(NacosSemanticAttributes.NAMESPACE, namingGrpcRedoService.getNamespace());
                    
                    switch (methodName) {
                        case "cacheInstanceForRedo": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    String instanceString;
                                    Object instanceArg = args[2];
                                    if (instanceArg instanceof Instance) {
                                        instanceString = instanceArg.toString();
                                    } else {
                                        List<?> instanceList = (List<?>) instanceArg;
                                        instanceString = StringUtils.join(instanceList, ", ");
                                    }
                                    span.setAttribute(NacosSemanticAttributes.INSTANCE, instanceString);
                                }
                                
                                result = method.invoke(namingGrpcRedoService, args);
                                
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
                        case "instanceRegistered":
                        case "instanceDeregister":
                        case "instanceDeregistered": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                }
                                
                                result = method.invoke(namingGrpcRedoService, args);
                                
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
                        case "cacheSubscriberForRedo":
                        case "subscriberRegistered":
                        case "subscriberDeregister":
                        case "isSubscriberRegistered":
                        case "removeSubscriberForRedo": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                    span.setAttribute(NacosSemanticAttributes.GROUP, (String) args[1]);
                                    span.setAttribute(NacosSemanticAttributes.CLUSTER, (String) args[2]);
                                }
                                
                                result = method.invoke(namingGrpcRedoService, args);
                                
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
                        case "getRegisteredInstancesByKey": {
                            Object result;
                            Span span = spanBuilder.startSpan();
                            try (Scope ignored = span.makeCurrent()) {
                                
                                if (span.isRecording()) {
                                    span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, (String) args[0]);
                                }
                                
                                result = method.invoke(namingGrpcRedoService, args);
                                
                                if (span.isRecording() && result instanceof InstanceRedoData) {
                                    Instance instance = ((InstanceRedoData) result).get();
                                    span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_RESULT,
                                            instance.toString());
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
                    
                    try {
                        return method.invoke(namingGrpcRedoService, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                });
    }
    
    private static boolean isFail(int code) {
        return code == HttpURLConnection.HTTP_INTERNAL_ERROR || code == HttpURLConnection.HTTP_BAD_GATEWAY
                || code == HttpURLConnection.HTTP_UNAVAILABLE || code == HttpURLConnection.HTTP_NOT_FOUND;
    }
}
