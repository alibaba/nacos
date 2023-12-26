/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 */

package com.alibaba.nacos.client.config;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.filter.IConfigFilter;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigRequest;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.alibaba.nacos.client.config.impl.LocalEncryptedDataKeyProcessor;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.config.utils.ContentUtils;
import com.alibaba.nacos.client.config.utils.ParamUtils;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.monitor.config.ConfigTrace;
import com.alibaba.nacos.client.monitor.delegate.config.ClientWorkerTraceDelegate;
import com.alibaba.nacos.client.monitor.delegate.config.ServerHttpAgentTraceDelegate;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.ValidatorUtils;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import com.alibaba.nacos.common.utils.StringUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Properties;

/**
 * Config Impl.
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosConfigService implements ConfigService {
    
    private static final Logger LOGGER = LogUtils.logger(NacosConfigService.class);
    
    private static final String UP = "UP";
    
    private static final String DOWN = "DOWN";
    
    /**
     * will be deleted in 2.0 later versions
     */
    @Deprecated
    HttpAgent agent;
    
    /**
     * long polling.
     */
    private final ClientWorker worker;
    
    private String namespace;
    
    private final ConfigFilterChainManager configFilterChainManager;
    
    public NacosConfigService(Properties properties) throws NacosException {
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ValidatorUtils.checkInitParam(clientProperties);
        
        initNamespace(clientProperties);
        this.configFilterChainManager = new ConfigFilterChainManager(clientProperties.asProperties());
        ServerListManager serverListManager = new ServerListManager(clientProperties);
        serverListManager.start();
        
        this.worker = new ClientWorkerTraceDelegate(this.configFilterChainManager, serverListManager, clientProperties);
        // will be deleted in 2.0 later versions
        agent = new ServerHttpAgentTraceDelegate(serverListManager);
        
    }
    
    private void initNamespace(NacosClientProperties properties) {
        namespace = ParamUtil.parseNamespace(properties);
        properties.setProperty(PropertyKeyConst.NAMESPACE, namespace);
    }
    
    @Override
    public String getConfig(String dataId, String group, long timeoutMs) throws NacosException {
        return getConfigInner(namespace, dataId, group, timeoutMs);
    }
    
    @Override
    public String getConfigAndSignListener(String dataId, String group, long timeoutMs, Listener listener)
            throws NacosException {
        group = StringUtils.isBlank(group) ? Constants.DEFAULT_GROUP : group.trim();
        
        ConfigResponse configResponse = worker.getServerConfig(dataId, group, worker.getAgentTenant(), timeoutMs,
                false);
        
        String content = configResponse.getContent();
        String encryptedDataKey = configResponse.getEncryptedDataKey();
        
        worker.addTenantListenersWithContent(dataId, group, content, encryptedDataKey, Arrays.asList(listener));
        
        // get a decryptContent, fix https://github.com/alibaba/nacos/issues/7039
        ConfigResponse cr = new ConfigResponse();
        cr.setDataId(dataId);
        cr.setGroup(group);
        cr.setContent(content);
        cr.setEncryptedDataKey(encryptedDataKey);
        configFilterChainManager.doFilter(null, cr);
        return cr.getContent();
    }
    
    @Override
    public void addListener(String dataId, String group, Listener listener) throws NacosException {
        worker.addTenantListeners(dataId, group, Arrays.asList(listener));
    }
    
    @Override
    public boolean publishConfig(String dataId, String group, String content) throws NacosException {
        return publishConfig(dataId, group, content, ConfigType.getDefaultType().getType());
    }
    
    @Override
    public boolean publishConfig(String dataId, String group, String content, String type) throws NacosException {
        return publishConfigInner(namespace, dataId, group, null, null, null, content, type, null);
    }
    
    @Override
    public boolean publishConfigCas(String dataId, String group, String content, String casMd5) throws NacosException {
        return publishConfigInner(namespace, dataId, group, null, null, null, content,
                ConfigType.getDefaultType().getType(), casMd5);
    }
    
    @Override
    public boolean publishConfigCas(String dataId, String group, String content, String casMd5, String type)
            throws NacosException {
        return publishConfigInner(namespace, dataId, group, null, null, null, content, type, casMd5);
    }
    
    @Override
    public boolean removeConfig(String dataId, String group) throws NacosException {
        return removeConfigInner(namespace, dataId, group, null);
    }
    
    @Override
    public void removeListener(String dataId, String group, Listener listener) {
        worker.removeTenantListener(dataId, group, listener);
    }
    
    private String getConfigInner(String tenant, String dataId, String group, long timeoutMs) throws NacosException {
        group = blank2defaultGroup(group);
        ParamUtils.checkKeyParam(dataId, group);
        ConfigResponse cr = new ConfigResponse();
        
        cr.setDataId(dataId);
        cr.setTenant(tenant);
        cr.setGroup(group);
        
        // We first try to use local failover content if exists.
        // A config content for failover is not created by client program automatically,
        // but is maintained by user.
        // This is designed for certain scenario like client emergency reboot,
        // changing config needed in the same time, while nacos server is down.
        String content = getFailoverWithTrace(worker.getAgentName(), dataId, group, tenant, false);
        
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get failover ok, dataId={}, group={}, tenant={}, config={}",
                    worker.getAgentName(), dataId, group, tenant, ContentUtils.truncateContent(content));
            cr.setContent(content);
            
            String encryptedDataKey = getFailoverWithTrace(agent.getName(), dataId, group, tenant, true);
            
            cr.setEncryptedDataKey(encryptedDataKey);
            configFilterChainManager.doFilter(null, cr);
            content = cr.getContent();
            return content;
        }
        
        try {
            
            ConfigResponse response = worker.getServerConfig(dataId, group, tenant, timeoutMs, false);
            
            cr.setContent(response.getContent());
            cr.setEncryptedDataKey(response.getEncryptedDataKey());
            configFilterChainManager.doFilter(null, cr);
            content = cr.getContent();
            
            return content;
        } catch (NacosException ioe) {
            if (NacosException.NO_RIGHT == ioe.getErrCode()) {
                throw ioe;
            }
            LOGGER.warn("[{}] [get-config] get from server error, dataId={}, group={}, tenant={}, msg={}",
                    worker.getAgentName(), dataId, group, tenant, ioe.toString());
        }
        
        content = getSnapshotWithTrace(worker.getAgentName(), dataId, group, tenant, false);
        
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get snapshot ok, dataId={}, group={}, tenant={}, config={}",
                    worker.getAgentName(), dataId, group, tenant, ContentUtils.truncateContent(content));
        }
        cr.setContent(content);
        
        String encryptedDataKey = getSnapshotWithTrace(agent.getName(), dataId, group, tenant, true);
        
        cr.setEncryptedDataKey(encryptedDataKey);
        configFilterChainManager.doFilter(null, cr);
        content = cr.getContent();
        return content;
    }
    
    /**
     * Get failover with trace.
     *
     * <p>Didn't use dynamic proxy here because of calling static method.
     *
     * @param envName     env name
     * @param dataId      data id
     * @param group       group
     * @param tenant      tenant
     * @param isEncrypted is encrypted
     * @return content
     */
    private String getFailoverWithTrace(String envName, String dataId, String group, String tenant,
            boolean isEncrypted) {
        
        SpanBuilder spanBuilder;
        if (isEncrypted) {
            spanBuilder = ConfigTrace.getClientConfigServiceSpanBuilder("getEncryptDataKeyFailover");
            spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, LocalEncryptedDataKeyProcessor.class.getName());
        } else {
            spanBuilder = ConfigTrace.getClientConfigServiceSpanBuilder("getFailover");
            spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, LocalConfigInfoProcessor.class.getName());
        }
        
        Span span = setSpanLocalConfigInfoAttributes(envName, dataId, group, tenant, spanBuilder);
        
        String content;
        try (Scope ignored = span.makeCurrent()) {
            
            if (isEncrypted) {
                content = LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(envName, dataId, group, tenant);
            } else {
                content = LocalConfigInfoProcessor.getFailover(envName, dataId, group, tenant);
            }
            
            if (span.isRecording()) {
                if (content == null) {
                    span.setStatus(StatusCode.ERROR, "Get failover failed");
                } else {
                    span.setStatus(StatusCode.OK, "Get failover ok");
                    span.setAttribute(NacosSemanticAttributes.CONTENT, content);
                }
            }
            
        } catch (Throwable e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
        
        return content;
    }
    
    /**
     * Get snapshot with trace.
     *
     * <p>Didn't use dynamic proxy here because of calling static method.
     *
     * @param envName     env name
     * @param dataId      data id
     * @param group       group
     * @param tenant      tenant
     * @param isEncrypted is encrypted
     * @return content
     */
    private String getSnapshotWithTrace(String envName, String dataId, String group, String tenant,
            boolean isEncrypted) {
        
        SpanBuilder spanBuilder;
        if (isEncrypted) {
            spanBuilder = ConfigTrace.getClientConfigServiceSpanBuilder("getEncryptDataKeySnapshot");
            spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, LocalEncryptedDataKeyProcessor.class.getName());
        } else {
            spanBuilder = ConfigTrace.getClientConfigServiceSpanBuilder("getSnapshot");
            spanBuilder.setAttribute(SemanticAttributes.CODE_NAMESPACE, LocalConfigInfoProcessor.class.getName());
        }
        
        Span span = setSpanLocalConfigInfoAttributes(envName, dataId, group, tenant, spanBuilder);
        
        String content;
        try (Scope ignored = span.makeCurrent()) {
            
            if (isEncrypted) {
                content = LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(envName, dataId, group, tenant);
            } else {
                content = LocalConfigInfoProcessor.getSnapshot(envName, dataId, group, tenant);
            }
            
            if (span.isRecording()) {
                if (content == null) {
                    span.setStatus(StatusCode.ERROR, "Get snapshot failed");
                } else {
                    span.setStatus(StatusCode.OK, "Get snapshot ok");
                    span.setAttribute(NacosSemanticAttributes.CONTENT, content);
                }
            }
            
        } catch (Throwable e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
        return content;
    }
    
    private Span setSpanLocalConfigInfoAttributes(String envName, String dataId, String group, String tenant,
            SpanBuilder spanBuilder) {
        spanBuilder.setAttribute(NacosSemanticAttributes.AGENT_NAME, envName);
        spanBuilder.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
        spanBuilder.setAttribute(NacosSemanticAttributes.GROUP, group);
        spanBuilder.setAttribute(NacosSemanticAttributes.TENANT, tenant);
        spanBuilder.setAttribute(NacosSemanticAttributes.NAMESPACE, namespace);
        
        return spanBuilder.startSpan();
    }
    
    private String blank2defaultGroup(String group) {
        return (StringUtils.isBlank(group)) ? Constants.DEFAULT_GROUP : group.trim();
    }
    
    private boolean removeConfigInner(String tenant, String dataId, String group, String tag) throws NacosException {
        group = blank2defaultGroup(group);
        ParamUtils.checkKeyParam(dataId, group);
        
        return worker.removeConfig(dataId, group, tenant, tag);
    }
    
    private boolean publishConfigInner(String tenant, String dataId, String group, String tag, String appName,
            String betaIps, String content, String type, String casMd5) throws NacosException {
        group = blank2defaultGroup(group);
        ParamUtils.checkParam(dataId, group, content);
        
        ConfigRequest cr = new ConfigRequest();
        cr.setDataId(dataId);
        cr.setTenant(tenant);
        cr.setGroup(group);
        cr.setContent(content);
        cr.setType(type);
        configFilterChainManager.doFilter(cr, null);
        content = cr.getContent();
        String encryptedDataKey = cr.getEncryptedDataKey();
        
        return worker.publishConfig(dataId, group, tenant, appName, tag, betaIps, content, encryptedDataKey, casMd5,
                type);
    }
    
    @Override
    public String getServerStatus() {
        boolean result = worker.isHealthServer();
        
        if (result) {
            return UP;
        } else {
            return DOWN;
        }
    }
    
    @Override
    public void addConfigFilter(IConfigFilter configFilter) {
        configFilterChainManager.addFilter(configFilter);
    }
    
    @Override
    public void shutDown() throws NacosException {
        worker.shutdown();
    }
}
