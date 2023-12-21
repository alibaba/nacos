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

package com.alibaba.nacos.client.monitor.delegate.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.client.monitor.naming.NamingTrace;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.StringUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.List;

/**
 * Opentelemetry Trace delegate for {@link NamingClientProxy}.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class NamingClientProxyTraceDelegate implements NamingClientProxy {
    
    private final NamingClientProxy namingClientProxyImpl;
    
    private final String namingClientType;
    
    /**
     * Constructor with a naming client proxy. This class will delegate all method for Opentelemetry trace.
     *
     * @param namingClientProxyImpl naming client proxy
     */
    public NamingClientProxyTraceDelegate(NamingClientProxy namingClientProxyImpl) {
        this.namingClientProxyImpl = namingClientProxyImpl;
        
        if (this.namingClientProxyImpl instanceof NamingGrpcClientProxy) {
            this.namingClientType = "grpc";
        } else if (this.namingClientProxyImpl instanceof NamingHttpClientProxy) {
            this.namingClientType = "http";
        } else {
            this.namingClientType = "delegate";
        }
    }
    
    /**
     * Init SpanBuilder with method name.
     *
     * @param methodName method name
     * @return SpanBuilder
     */
    private SpanBuilder initSpanBuilder(String methodName) {
        SpanBuilder spanBuilder;
        if (namingClientType.equals("grpc") || namingClientType.equals("http")) {
            spanBuilder = NamingTrace.getClientNamingWorkerSpanBuilder(methodName);
        } else {
            spanBuilder = NamingTrace.getClientNamingServiceSpanBuilder(methodName);
        }
        
        spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, namingClientProxyImpl.getClass().getName());
        spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
        spanBuilder.setAttribute(NacosSemanticAttributes.NAMESPACE, namingClientProxyImpl.getNamespace());
        spanBuilder.setAttribute(NacosSemanticAttributes.NAMING_CLIENT_TYPE, namingClientType);
        
        return spanBuilder;
    }
    
    /**
     * Register an instance to service with specified instance properties.
     *
     * @param serviceName name of service
     * @param groupName   group of service
     * @param instance    instance to register
     * @throws NacosException nacos exception
     */
    @Override
    public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {
        Span span = initSpanBuilder("registerService").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.INSTANCE, instance.toString());
            }
            
            namingClientProxyImpl.registerService(serviceName, groupName, instance);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Batch register instance to service with specified instance properties.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instances   instance
     * @throws NacosException nacos exception
     * @since 2.1.1
     */
    @Override
    public void batchRegisterService(String serviceName, String groupName, List<Instance> instances)
            throws NacosException {
        Span span = initSpanBuilder("batchRegisterService").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.INSTANCE, StringUtils.join(instances, ", "));
            }
            
            namingClientProxyImpl.batchRegisterService(serviceName, groupName, instances);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Batch deRegister instance to service with specified instance properties.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instances   deRegister instance
     * @throws NacosException nacos exception
     * @since 2.2.0
     */
    @Override
    public void batchDeregisterService(String serviceName, String groupName, List<Instance> instances)
            throws NacosException {
        Span span = initSpanBuilder("batchDeregisterService").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.INSTANCE, StringUtils.join(instances, ", "));
            }
            
            namingClientProxyImpl.batchDeregisterService(serviceName, groupName, instances);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Deregister instance from a service.
     *
     * @param serviceName name of service
     * @param groupName   group name
     * @param instance    instance
     * @throws NacosException nacos exception
     */
    @Override
    public void deregisterService(String serviceName, String groupName, Instance instance) throws NacosException {
        Span span = initSpanBuilder("deregisterService").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.INSTANCE, instance.toString());
            }
            
            namingClientProxyImpl.deregisterService(serviceName, groupName, instance);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Update instance to service.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instance    instance
     * @throws NacosException nacos exception
     */
    @Override
    public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
        Span span = initSpanBuilder("updateInstance").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.PUT);
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.INSTANCE, instance.toString());
            }
            
            namingClientProxyImpl.updateInstance(serviceName, groupName, instance);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Query instance list.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters
     * @param udpPort     udp port
     * @param healthyOnly healthy only
     * @return service info
     * @throws NacosException nacos exception
     */
    @Override
    public ServiceInfo queryInstancesOfService(String serviceName, String groupName, String clusters, int udpPort,
            boolean healthyOnly) throws NacosException {
        Span span = initSpanBuilder("queryInstancesOfService").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.CLUSTER, clusters);
                span.setAttribute(NacosSemanticAttributes.UDP_PORT, udpPort);
                span.setAttribute(NacosSemanticAttributes.HEALTHY_ONLY, healthyOnly);
            }
            
            ServiceInfo serviceInfo = namingClientProxyImpl.queryInstancesOfService(serviceName, groupName, clusters,
                    udpPort, healthyOnly);
            
            if (span.isRecording() && serviceInfo != null) {
                span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_RESULT,
                        StringUtils.join(serviceInfo.getHosts(), ", "));
            }
            return serviceInfo;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Query Service.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @return service
     * @throws NacosException nacos exception
     */
    @Override
    public Service queryService(String serviceName, String groupName) throws NacosException {
        Span span = initSpanBuilder("queryService").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.GET);
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
            }
            
            return namingClientProxyImpl.queryService(serviceName, groupName);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Create service.
     *
     * @param service  service
     * @param selector selector
     * @throws NacosException nacos exception
     */
    @Override
    public void createService(Service service, AbstractSelector selector) throws NacosException {
        Span span = initSpanBuilder("createService").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.POST);
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, service.getName());
                span.setAttribute(NacosSemanticAttributes.GROUP, service.getGroupName());
            }
            
            namingClientProxyImpl.createService(service, selector);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Delete service.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @return true if delete ok
     * @throws NacosException nacos exception
     */
    @Override
    public boolean deleteService(String serviceName, String groupName) throws NacosException {
        Span span = initSpanBuilder("deleteService").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.DELETE);
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
            }
            
            return namingClientProxyImpl.deleteService(serviceName, groupName);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Update service.
     *
     * @param service  service
     * @param selector selector
     * @throws NacosException nacos exception
     */
    @Override
    public void updateService(Service service, AbstractSelector selector) throws NacosException {
        Span span = initSpanBuilder("updateService").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.PUT);
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, service.getName());
                span.setAttribute(NacosSemanticAttributes.GROUP, service.getGroupName());
            }
            
            namingClientProxyImpl.updateService(service, selector);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Get service list.
     *
     * @param pageNo    page number
     * @param pageSize  size per page
     * @param groupName group name of service
     * @param selector  selector
     * @return list of service
     * @throws NacosException nacos exception
     */
    @Override
    public ListView<String> getServiceList(int pageNo, int pageSize, String groupName, AbstractSelector selector)
            throws NacosException {
        Span span = initSpanBuilder("getServiceList").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                if (namingClientType.equals("grpc")) {
                    span.setAttribute(SemanticAttributes.RPC_SYSTEM, "grpc");
                } else if (namingClientType.equals("http")) {
                    span.setAttribute(SemanticAttributes.HTTP_METHOD, HttpMethod.GET);
                }
                span.setAttribute(NacosSemanticAttributes.PAGE_NO, pageNo);
                span.setAttribute(NacosSemanticAttributes.PAGE_SIZE, pageSize);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
            }
            
            ListView<String> serviceList = namingClientProxyImpl.getServiceList(pageNo, pageSize, groupName, selector);
            
            if (span.isRecording() && serviceList != null) {
                span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_RESULT,
                        StringUtils.join(serviceList.getData(), ", "));
            }
            return serviceList;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Subscribe service.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters, current only support subscribe all clusters, maybe deprecated
     * @return current service info of subscribe service
     * @throws NacosException nacos exception
     */
    @Override
    public ServiceInfo subscribe(String serviceName, String groupName, String clusters) throws NacosException {
        Span span = initSpanBuilder("subscribe").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.CLUSTER, clusters);
            }
            
            return namingClientProxyImpl.subscribe(serviceName, groupName, clusters);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Unsubscribe service.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters, current only support subscribe all clusters, maybe deprecated
     * @throws NacosException nacos exception
     */
    @Override
    public void unsubscribe(String serviceName, String groupName, String clusters) throws NacosException {
        Span span = initSpanBuilder("unsubscribe").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.CLUSTER, clusters);
            }
            
            namingClientProxyImpl.unsubscribe(serviceName, groupName, clusters);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Judge whether service has been subscribed.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters, current only support subscribe all clusters, maybe deprecated
     * @return {@code true} if subscribed, otherwise {@code false}
     * @throws NacosException nacos exception
     */
    @Override
    public boolean isSubscribed(String serviceName, String groupName, String clusters) throws NacosException {
        Span span = initSpanBuilder("isSubscribed").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.CLUSTER, clusters);
            }
            
            return namingClientProxyImpl.isSubscribed(serviceName, groupName, clusters);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Check Server healthy.
     *
     * @return true if server is healthy
     */
    @Override
    public boolean serverHealthy() {
        Span span = initSpanBuilder("serverHealthy").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            boolean isHealthy = namingClientProxyImpl.serverHealthy();
            
            if (span.isRecording()) {
                if (isHealthy) {
                    span.setStatus(StatusCode.OK, "Server is up");
                } else {
                    span.setStatus(StatusCode.ERROR, "Server is down");
                }
            }
            
            return isHealthy;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Get namespace of naming client.
     *
     * @return namespace
     */
    @Override
    public String getNamespace() {
        return namingClientProxyImpl.getNamespace();
    }
    
    /**
     * Shutdown the Resources, such as Thread Pool.
     *
     * @throws NacosException exception.
     */
    @Override
    public void shutdown() throws NacosException {
        namingClientProxyImpl.shutdown();
    }
}
