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

import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.impl.CacheData;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.monitor.config.ConfigTrace;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.List;

/**
 * Opentelemetry Trace delegate for {@link ClientWorker}.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class ClientWorkerTraceDelegate extends ClientWorker {
    
    public ClientWorkerTraceDelegate(final ConfigFilterChainManager configFilterChainManager,
            ServerListManager serverListManager, final NacosClientProperties properties) throws NacosException {
        super(configFilterChainManager, serverListManager, properties);
    }
    
    /**
     * Init Service level SpanBuilder with method name.
     *
     * @param methodName method name
     * @return SpanBuilder
     */
    private SpanBuilder initServiceSpanBuilder(String methodName) {
        SpanBuilder spanBuilder = ConfigTrace.getClientConfigServiceSpanBuilder(methodName);
        spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, super.getClass().getName());
        spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
        spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, super.getAgentName());
        return spanBuilder;
    }
    
    
    /**
     * Add listeners for data.
     *
     * @param dataId    dataId of data
     * @param group     group of data
     * @param listeners listeners
     */
    @Override
    public void addListeners(String dataId, String group, List<? extends Listener> listeners) throws NacosException {
        Span span = initServiceSpanBuilder("addListeners").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span.setAttribute(NacosSemanticAttributes.GROUP, group);
            }
            
            super.addListeners(dataId, group, listeners);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Add listener to config.
     *
     * @param dataId           dataId
     * @param group            group
     * @param content          content
     * @param encryptedDataKey encryptedDataKey
     * @param listeners        listener
     * @throws NacosException NacosException
     */
    @Override
    public void addTenantListenersWithContent(String dataId, String group, String content, String encryptedDataKey,
            List<? extends Listener> listeners) throws NacosException {
        Span span = initServiceSpanBuilder("addTenantListenersWithContent").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span.setAttribute(NacosSemanticAttributes.GROUP, group);
                span.setAttribute(NacosSemanticAttributes.CONTENT, content);
            }
            
            super.addTenantListenersWithContent(dataId, group, content, encryptedDataKey, listeners);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Add listener to config.
     *
     * @param dataId    dataId
     * @param group     group
     * @param listeners listener
     * @throws NacosException NacosException
     */
    @Override
    public void addTenantListeners(String dataId, String group, List<? extends Listener> listeners)
            throws NacosException {
        Span span = initServiceSpanBuilder("addTenantListeners").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span.setAttribute(NacosSemanticAttributes.GROUP, group);
            }
            
            super.addTenantListeners(dataId, group, listeners);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Remove listener from config.
     *
     * @param dataId   dataId
     * @param group    group
     * @param listener listener
     */
    @Override
    public void removeTenantListener(String dataId, String group, Listener listener) {
        Span span = initServiceSpanBuilder("removeTenantListener").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span.setAttribute(NacosSemanticAttributes.GROUP, group);
            }
            
            super.removeTenantListener(dataId, group, listener);
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Get config from server.
     *
     * @param dataId      dataId
     * @param group       group
     * @param tenant      tenant
     * @param readTimeout readTimeout
     * @param notify      notify
     * @return ConfigResponse
     * @throws NacosException NacosException
     */
    @Override
    public ConfigResponse getServerConfig(String dataId, String group, String tenant, long readTimeout, boolean notify)
            throws NacosException {
        Span span = initServiceSpanBuilder("getServerConfig").startSpan();
        ConfigResponse result;
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span.setAttribute(NacosSemanticAttributes.GROUP, group);
                span.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                span.setAttribute(NacosSemanticAttributes.TIMEOUT_MS, readTimeout);
                span.setAttribute(NacosSemanticAttributes.NOTIFY, notify);
            }
            
            result = super.getServerConfig(dataId, group, tenant, readTimeout, notify);
            
            if (span.isRecording() && result != null) {
                span.setAttribute(NacosSemanticAttributes.CONTENT, result.getContent());
            }
            return result;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Remove all listeners from config.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @param tag    tag
     * @return boolean
     * @throws NacosException NacosException
     */
    @Override
    public boolean removeConfig(String dataId, String group, String tenant, String tag) throws NacosException {
        Span span = initServiceSpanBuilder("removeConfig").startSpan();
        boolean result;
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span.setAttribute(NacosSemanticAttributes.GROUP, group);
                span.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                span.setAttribute(NacosSemanticAttributes.TAG, tag);
            }
            
            result = super.removeConfig(dataId, group, tenant, tag);
            
            if (span.isRecording()) {
                if (result) {
                    span.setStatus(StatusCode.OK, "Remove config success");
                } else {
                    span.setStatus(StatusCode.ERROR, "Remove config failed");
                }
            }
            return result;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Remove all listeners from config.
     *
     * @param dataId           dataId
     * @param group            group
     * @param tenant           tenant
     * @param appName          appName
     * @param tag              tag
     * @param betaIps          betaIps
     * @param content          content
     * @param encryptedDataKey encryptedDataKey
     * @param casMd5           casMd5
     * @param type             type
     * @return boolean
     * @throws NacosException NacosException
     */
    @Override
    public boolean publishConfig(String dataId, String group, String tenant, String appName, String tag, String betaIps,
            String content, String encryptedDataKey, String casMd5, String type) throws NacosException {
        Span span = initServiceSpanBuilder("publishConfig").startSpan();
        boolean result;
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span.setAttribute(NacosSemanticAttributes.GROUP, group);
                span.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                span.setAttribute(NacosSemanticAttributes.APPLICATION_NAME, appName);
                span.setAttribute(NacosSemanticAttributes.TAG, tag);
                span.setAttribute(NacosSemanticAttributes.CONTENT, content);
                span.setAttribute(NacosSemanticAttributes.CONFIG_TYPE, type);
            }
            
            result = super.publishConfig(dataId, group, tenant, appName, tag, betaIps, content, encryptedDataKey,
                    casMd5, type);
            
            if (span.isRecording()) {
                if (result) {
                    span.setStatus(StatusCode.OK, "Publish config success");
                } else {
                    span.setStatus(StatusCode.ERROR, "Publish config failed");
                }
            }
            return result;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Check whether the server is health.
     *
     * @return boolean
     */
    @Override
    public boolean isHealthServer() {
        Span span = initServiceSpanBuilder("isHealthServer").startSpan();
        boolean result;
        try (Scope ignored = span.makeCurrent()) {
            
            result = super.isHealthServer();
            
            if (span.isRecording()) {
                if (result) {
                    span.setStatus(StatusCode.OK, "Server is healthy");
                } else {
                    span.setStatus(StatusCode.ERROR, "Server is not healthy");
                }
            }
            return result;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
    
    public static class AddCacheDataWarp {
        
        private static final String METHOD_NAME = "addCacheDataIfAbsent";
        
        /**
         * Init Worker level SpanBuilder with method name.
         *
         * @param clientWorker ClientWorker
         * @return SpanBuilder
         */
        private static SpanBuilder initWorkerSpanBuilder(ClientWorker clientWorker) {
            SpanBuilder spanBuilder = ConfigTrace.getClientConfigWorkerSpanBuilder(METHOD_NAME);
            spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, clientWorker.getClass().getName());
            spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, METHOD_NAME);
            spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, clientWorker.getAgentName());
            return spanBuilder;
        }
        
        /**
         * Warp addCacheDataIfAbsent for tracing.
         *
         * @param clientWorker ClientWorker
         * @param dataId       dataId
         * @param group        group
         * @return CacheData
         */
        public static CacheData warp(ClientWorker clientWorker, String dataId, String group) {
            Span span = initWorkerSpanBuilder(clientWorker).startSpan();
            CacheData result;
            try (Scope ignored = span.makeCurrent()) {
                
                if (span.isRecording()) {
                    span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                    span.setAttribute(NacosSemanticAttributes.GROUP, group);
                    span.setAttribute(NacosSemanticAttributes.TENANT, clientWorker.getAgentTenant());
                }
                
                result = clientWorker.addCacheDataIfAbsent(dataId, group);
                
                if (span.isRecording()) {
                    span.setAttribute(NacosSemanticAttributes.CONTENT, result.getContent());
                }
                return result;
                
            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
                throw e;
            } finally {
                span.end();
            }
        }
        
        /**
         * Warp addCacheDataIfAbsent for tracing with tenant.
         *
         * @param clientWorker ClientWorker
         * @param dataId       dataId
         * @param group        group
         * @param tenant       tenant
         * @return CacheData
         */
        public static CacheData warp(ClientWorker clientWorker, String dataId, String group, String tenant) {
            Span span = initWorkerSpanBuilder(clientWorker).startSpan();
            CacheData result;
            try (Scope ignored = span.makeCurrent()) {
                
                if (span.isRecording()) {
                    span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                    span.setAttribute(NacosSemanticAttributes.GROUP, group);
                    span.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                }
                
                result = clientWorker.addCacheDataIfAbsent(dataId, group);
                
                if (span.isRecording()) {
                    span.setAttribute(NacosSemanticAttributes.CONTENT, result.getContent());
                }
                return result;
                
            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
                throw e;
            } finally {
                span.end();
            }
        }
    }
}
