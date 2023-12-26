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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.monitor.TraceMonitor;
import com.alibaba.nacos.client.monitor.config.ConfigTrace;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import com.alibaba.nacos.common.remote.client.RpcClient;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Opentelemetry Trace delegate for {@link com.alibaba.nacos.client.config.impl.ClientWorker.ConfigRpcTransportClient}.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class ConfigRpcTransportClientProxyTraceDelegate implements ConfigRpcTransportClientProxy {
    
    private final ConfigRpcTransportClientProxy configRpcTransportClientImpl;
    
    public ConfigRpcTransportClientProxyTraceDelegate(ConfigRpcTransportClientProxy impl) {
        this.configRpcTransportClientImpl = impl;
    }
    
    /**
     * Init Worker level SpanBuilder with method name.
     *
     * @param methodName method name
     * @return SpanBuilder
     */
    private SpanBuilder initWorkerSpanBuilder(String methodName) {
        SpanBuilder spanBuilder = ConfigTrace.getClientConfigWorkerSpanBuilder(methodName);
        spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE,
                this.configRpcTransportClientImpl.getClass().getName());
        spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, methodName);
        spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, this.configRpcTransportClientImpl.getName());
        return spanBuilder;
    }
    
    /**
     * Query config from server.
     *
     * @param dataId       dataId
     * @param group        group
     * @param tenant       tenant
     * @param readTimeouts readTimeouts
     * @param notify       notify
     * @return ConfigResponse
     * @throws NacosException NacosException
     */
    @Override
    public ConfigResponse queryConfig(String dataId, String group, String tenant, long readTimeouts, boolean notify)
            throws NacosException {
        Span span = initWorkerSpanBuilder("queryConfig").startSpan();
        ConfigResponse result;
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span.setAttribute(NacosSemanticAttributes.GROUP, group);
                span.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                span.setAttribute(NacosSemanticAttributes.TIMEOUT_MS, readTimeouts);
                span.setAttribute(NacosSemanticAttributes.NOTIFY, notify);
            }
            
            result = this.configRpcTransportClientImpl.queryConfig(dataId, group, tenant, readTimeouts, notify);
            
            if (span.isRecording()) {
                if (result == null || result.getConfigType() == null) {
                    span.setStatus(StatusCode.ERROR, "Config not found");
                } else {
                    span.setStatus(StatusCode.OK, "Query Config success");
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
     * Publish config to server.
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
        Span span = initWorkerSpanBuilder("publishConfig").startSpan();
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
            
            result = this.configRpcTransportClientImpl.publishConfig(dataId, group, tenant, appName, tag, betaIps,
                    content, encryptedDataKey, casMd5, type);
            
            if (span.isRecording()) {
                if (result) {
                    span.setStatus(StatusCode.OK, "Publish Config success");
                } else {
                    span.setStatus(StatusCode.ERROR, "Publish Config failed");
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
     * Remove config from server.
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
        Span span = initWorkerSpanBuilder("removeConfig").startSpan();
        boolean result;
        try (Scope ignored = span.makeCurrent()) {
            
            if (span.isRecording()) {
                span.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span.setAttribute(NacosSemanticAttributes.GROUP, group);
                span.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                span.setAttribute(NacosSemanticAttributes.TAG, tag);
            }
            
            result = this.configRpcTransportClientImpl.removeConfig(dataId, group, tenant, tag);
            
            if (span.isRecording()) {
                if (result) {
                    span.setStatus(StatusCode.OK, "Remove Config success");
                } else {
                    span.setStatus(StatusCode.ERROR, "Remove Config failed");
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
     * Rpc request.
     *
     * @param rpcClientInner rpcClientInner
     * @param request        request
     * @param timeoutMills   timeoutMills
     * @throws NacosException NacosException
     */
    @Override
    public Response rpcRequest(RpcClient rpcClientInner, Request request, long timeoutMills) throws NacosException {
        return RequestProxyWarp.warp(configRpcTransportClientImpl, rpcClientInner, request, timeoutMills);
    }
    
    
    /**
     * Notify listen config.
     */
    @Override
    public void notifyListenConfig() {
        this.configRpcTransportClientImpl.notifyListenConfig();
    }
    
    /**
     * Get tenant.
     *
     * @return tenant
     */
    @Override
    public String getTenant() {
        return this.configRpcTransportClientImpl.getTenant();
    }
    
    /**
     * Remove cache.
     *
     * @param dataId dataId
     * @param group  group
     */
    @Override
    public void removeCache(String dataId, String group) {
        this.configRpcTransportClientImpl.removeCache(dataId, group);
    }
    
    /**
     * Get agent name.
     *
     * @return agent name
     */
    @Override
    public String getName() {
        return this.configRpcTransportClientImpl.getName();
    }
    
    /**
     * Get server list manager.
     *
     * @return server list manager
     */
    @Override
    public ServerListManager getServerListManager() {
        return this.configRpcTransportClientImpl.getServerListManager();
    }
    
    /**
     * Set executor.
     *
     * @param executor executor
     */
    @Override
    public void setExecutor(ScheduledExecutorService executor) {
        this.configRpcTransportClientImpl.setExecutor(executor);
    }
    
    /**
     * Start agent.
     *
     * @throws NacosException NacosException
     */
    @Override
    public void start() throws NacosException {
        this.configRpcTransportClientImpl.start();
    }
    
    /**
     * Check whether the server is health.
     *
     * @return boolean
     */
    @Override
    public boolean isHealthServer() {
        return this.configRpcTransportClientImpl.isHealthServer();
    }
    
    /**
     * Shutdown the Resources, such as Thread Pool.
     *
     * @throws NacosException exception.
     */
    @Override
    public void shutdown() throws NacosException {
        this.configRpcTransportClientImpl.shutdown();
    }
    
    public static class RequestProxyWarp {
        
        private static final String METHOD_NAME = "requestProxy";
        
        /**
         * Init Rpc level SpanBuilder with method name.
         *
         * @param client rpc client
         * @return SpanBuilder
         */
        private static SpanBuilder initRpcSpanBuilder(ConfigRpcTransportClientProxy client, String rpcType) {
            SpanBuilder spanBuilder = ConfigTrace.getClientConfigRpcSpanBuilder(rpcType);
            spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, client.getClass().getName());
            spanBuilder.setAttribute(SemanticAttributes.CODE_FUNCTION, METHOD_NAME);
            spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, client.getName());
            return spanBuilder;
        }
        
        /**
         * Warp requestProxy for tracing.
         *
         * @param client         rpc client
         * @param rpcClientInner rpcClientInner
         * @param request        request
         * @param timeoutMills   timeoutMills
         * @return CacheData
         */
        public static Response warp(ConfigRpcTransportClientProxy client, RpcClient rpcClientInner, Request request,
                long timeoutMills) throws NacosException {
            String rpcSystem = rpcClientInner.getConnectionType().getType();
            Span span = initRpcSpanBuilder(client, rpcSystem).startSpan();
            Response result;
            try (Scope ignored = span.makeCurrent()) {
                
                if (span.isRecording()) {
                    span.setAttribute(NacosSemanticAttributes.TIMEOUT_MS, timeoutMills);
                    span.setAttribute(SemanticAttributes.RPC_SYSTEM, rpcSystem.toLowerCase());
                    if (rpcClientInner.getCurrentServer() != null) {
                        span.setAttribute(NacosSemanticAttributes.SERVER_ADDRESS,
                                rpcClientInner.getCurrentServer().getAddress());
                    }
                    TraceMonitor.getOpenTelemetry().getPropagators().getTextMapPropagator()
                            .inject(Context.current(), request.getHeaders(), TraceMonitor.getRpcContextSetter());
                }
                
                result = client.rpcRequest(rpcClientInner, request, timeoutMills);
                
                if (span.isRecording()) {
                    if (result == null) {
                        span.setStatus(StatusCode.ERROR, "Request failed: result is null");
                    } else if (result.isSuccess()) {
                        span.setStatus(StatusCode.OK, "Request success");
                    } else {
                        span.setStatus(StatusCode.ERROR,
                                "Request failed: " + result.getErrorCode() + ": " + result.getMessage());
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
    }
    
}
