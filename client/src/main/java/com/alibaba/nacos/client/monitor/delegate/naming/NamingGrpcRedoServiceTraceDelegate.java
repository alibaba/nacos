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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.monitor.naming.NamingTrace;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.redo.NamingGrpcRedoService;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.InstanceRedoData;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import com.alibaba.nacos.common.utils.StringUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.List;

/**
 * Opentelemetry Trace delegate for {@link NamingGrpcRedoService}.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class NamingGrpcRedoServiceTraceDelegate extends NamingGrpcRedoService {
    
    public NamingGrpcRedoServiceTraceDelegate(NamingGrpcClientProxy clientProxy) {
        super(clientProxy);
    }
    
    /**
     * Init SpanBuilder with method name.
     *
     * @param methodName method name
     * @return SpanBuilder
     */
    private SpanBuilder initSpanBuilder(String methodName) {
        SpanBuilder spanBuilder = NamingTrace.getClientNamingWorkerSpanBuilder(methodName);
        spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, super.getClass().getName());
        spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
        spanBuilder.setAttribute(NacosSemanticAttributes.NAMESPACE, super.getNamespace());
        return spanBuilder;
    }
    
    /**
     * Cache registered instance for redo.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instance    registered instance
     */
    @Override
    public void cacheInstanceForRedo(String serviceName, String groupName, Instance instance) {
        Span span = initSpanBuilder("cacheInstanceForRedo").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.INSTANCE, instance.toString());
            }
            
            super.cacheInstanceForRedo(serviceName, groupName, instance);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Cache registered instances for redo.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instances   batch registered instance
     */
    @Override
    public void cacheInstanceForRedo(String serviceName, String groupName, List<Instance> instances) {
        Span span = initSpanBuilder("cacheInstanceForRedo").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.INSTANCE, StringUtils.join(instances, ", "));
            }
            
            super.cacheInstanceForRedo(serviceName, groupName, instances);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Instance register successfully, mark registered status as {@code true}.
     *
     * @param serviceName service name
     * @param groupName   group name
     */
    @Override
    public void instanceRegistered(String serviceName, String groupName) {
        Span span = initSpanBuilder("instanceRegistered").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
            }
            
            super.instanceRegistered(serviceName, groupName);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Instance deregister, mark unregistering status as {@code true}.
     *
     * @param serviceName service name
     * @param groupName   group name
     */
    @Override
    public void instanceDeregister(String serviceName, String groupName) {
        Span span = initSpanBuilder("instanceDeregister").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
            }
            
            super.instanceDeregister(serviceName, groupName);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Instance deregister finished, mark unregistered status.
     *
     * @param serviceName service name
     * @param groupName   group name
     */
    @Override
    public void instanceDeregistered(String serviceName, String groupName) {
        Span span = initSpanBuilder("instanceDeregistered").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
            }
            
            super.instanceDeregistered(serviceName, groupName);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Cache subscriber for redo.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param cluster     cluster
     */
    @Override
    public void cacheSubscriberForRedo(String serviceName, String groupName, String cluster) {
        Span span = initSpanBuilder("cacheSubscriberForRedo").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.CLUSTER, cluster);
            }
            
            super.cacheSubscriberForRedo(serviceName, groupName, cluster);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Subscriber register successfully, mark registered status as {@code true}.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param cluster     cluster
     */
    @Override
    public void subscriberRegistered(String serviceName, String groupName, String cluster) {
        Span span = initSpanBuilder("subscriberRegistered").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.CLUSTER, cluster);
            }
            
            super.subscriberRegistered(serviceName, groupName, cluster);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Subscriber deregister, mark unregistering status as {@code true}.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param cluster     cluster
     */
    @Override
    public void subscriberDeregister(String serviceName, String groupName, String cluster) {
        Span span = initSpanBuilder("subscriberDeregister").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.CLUSTER, cluster);
            }
            
            super.subscriberDeregister(serviceName, groupName, cluster);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Judge subscriber has registered to server.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param cluster     cluster
     * @return {@code true} if subscribed, otherwise {@code false}
     */
    @Override
    public boolean isSubscriberRegistered(String serviceName, String groupName, String cluster) {
        Span span = initSpanBuilder("isSubscriberRegistered").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.CLUSTER, cluster);
            }
            
            return super.isSubscriberRegistered(serviceName, groupName, cluster);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Remove subscriber for redo.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param cluster     cluster
     */
    @Override
    public void removeSubscriberForRedo(String serviceName, String groupName, String cluster) {
        Span span = initSpanBuilder("removeSubscriberForRedo").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, serviceName);
                span.setAttribute(NacosSemanticAttributes.GROUP, groupName);
                span.setAttribute(NacosSemanticAttributes.CLUSTER, cluster);
            }
            
            super.removeSubscriberForRedo(serviceName, groupName, cluster);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }
    
    /**
     * get Cache service.
     *
     * @return cache service
     */
    @Override
    public InstanceRedoData getRegisteredInstancesByKey(String combinedServiceName) {
        Span span = initSpanBuilder("getRegisteredInstancesByKey").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.SERVICE_NAME, combinedServiceName);
            }
            
            InstanceRedoData instanceRedoData = super.getRegisteredInstancesByKey(combinedServiceName);
            
            if (span.isRecording() && instanceRedoData != null) {
                span.setAttribute(NacosSemanticAttributes.RequestAttributes.REQUEST_RESULT,
                        instanceRedoData.get().toString());
            }
            return instanceRedoData;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }
}
