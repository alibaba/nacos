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
import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.alibaba.nacos.client.config.impl.LocalEncryptedDataKeyProcessor;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.config.utils.ContentUtils;
import com.alibaba.nacos.client.config.utils.ParamUtils;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.monitor.TraceDynamicProxy;
import com.alibaba.nacos.client.monitor.config.ClientWorkerTraceProxy;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import com.alibaba.nacos.client.monitor.config.ConfigTrace;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.ValidatorUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
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
    ServerHttpAgent agent = null;
    
    /**
     * long polling.
     */
    private final ClientWorkerTraceProxy worker;
    
    private String namespace;
    
    private final ConfigFilterChainManager configFilterChainManager;
    
    public NacosConfigService(Properties properties) throws NacosException {
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ValidatorUtils.checkInitParam(clientProperties);
        
        initNamespace(clientProperties);
        this.configFilterChainManager = new ConfigFilterChainManager(clientProperties.asProperties());
        ServerListManager serverListManager = new ServerListManager(clientProperties);
        serverListManager.start();
        
        this.worker = TraceDynamicProxy.getClientWorkerTraceProxy(
                new ClientWorker(this.configFilterChainManager, serverListManager, clientProperties));
        // will be deleted in 2.0 later versions
        agent = new ServerHttpAgent(serverListManager);
        
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
        
        ConfigResponse configResponse;
        Span span0 = ConfigTrace.getClientConfigServiceSpan("queryConfig");
        try (Scope ignored = span0.makeCurrent()) {
            
            if (span0.isRecording()) {
                span0.setAttribute(NacosSemanticAttributes.FUNCTION_CURRENT_NAME,
                        "com.alibaba.nacos.client.config.NacosConfigService.getConfigAndSignListener()");
                span0.setAttribute(NacosSemanticAttributes.FUNCTION_CALLED_NAME,
                        "com.alibaba.nacos.client.config.impl.ClientWorker.ConfigRpcTransportClient.queryConfig()");
                span0.setAttribute(NacosSemanticAttributes.AGENT_NAME, worker.getAgentName());
                span0.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span0.setAttribute(NacosSemanticAttributes.GROUP, group);
                span0.setAttribute(NacosSemanticAttributes.TENANT, worker.getAgent().getTenant());
                span0.setAttribute(NacosSemanticAttributes.NAMESPACE, namespace);
                span0.setAttribute(NacosSemanticAttributes.TIMEOUT_MS, timeoutMs);
            }
            
            configResponse = worker.getAgent()
                    .queryConfig(dataId, group, worker.getAgent().getTenant(), timeoutMs, false);
            
        } catch (NacosException e) {
            span0.recordException(e);
            span0.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span0.end();
        }
        
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
        String content;
        
        Span span0 = ConfigTrace.getClientConfigServiceSpan("getFailoverConfig");
        try (Scope ignored = span0.makeCurrent()) {
            
            if (span0.isRecording()) {
                span0.setAttribute(NacosSemanticAttributes.FUNCTION_CURRENT_NAME,
                        "com.alibaba.nacos.client.config.NacosConfigService.getConfigInner()");
                span0.setAttribute(NacosSemanticAttributes.FUNCTION_CALLED_NAME,
                        "com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor.getFailover()");
                span0.setAttribute(NacosSemanticAttributes.AGENT_NAME, worker.getAgentName());
                span0.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span0.setAttribute(NacosSemanticAttributes.GROUP, group);
                span0.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                span0.setAttribute(NacosSemanticAttributes.NAMESPACE, namespace);
            }
            
            content = LocalConfigInfoProcessor.getFailover(worker.getAgentName(), dataId, group, tenant);
            
            if (content != null) {
                span0.setStatus(StatusCode.OK, "get failover ok");
                if (span0.isRecording()) {
                    span0.setAttribute(NacosSemanticAttributes.CONTENT, content);
                }
            } else {
                span0.setStatus(StatusCode.ERROR, "get failover failed");
            }
            
        } catch (Throwable e) {
            span0.recordException(e);
            span0.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span0.end();
        }
        
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get failover ok, dataId={}, group={}, tenant={}, config={}",
                    worker.getAgentName(), dataId, group, tenant, ContentUtils.truncateContent(content));
            cr.setContent(content);
            
            String encryptedDataKey;
            Span span1 = ConfigTrace.getClientConfigServiceSpan("getEncryptDataKeyFailover");
            try (Scope ignored = span1.makeCurrent()) {
                
                if (span1.isRecording()) {
                    span1.setAttribute(NacosSemanticAttributes.FUNCTION_CURRENT_NAME,
                            "com.alibaba.nacos.client.config.NacosConfigService.getConfigInner()");
                    span1.setAttribute(NacosSemanticAttributes.FUNCTION_CALLED_NAME,
                            "com.alibaba.nacos.client.config.impl.LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover()");
                    span1.setAttribute(NacosSemanticAttributes.AGENT_NAME, worker.getAgentName());
                    span1.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                    span1.setAttribute(NacosSemanticAttributes.GROUP, group);
                    span1.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                    span1.setAttribute(NacosSemanticAttributes.NAMESPACE, namespace);
                }
                
                encryptedDataKey = LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(agent.getName(), dataId,
                        group, tenant);
                
            } catch (Throwable e) {
                span1.recordException(e);
                span1.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
                throw e;
            } finally {
                span1.end();
            }
            
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
        
        Span span3 = ConfigTrace.getClientConfigServiceSpan("getSnapshotConfig");
        try (Scope ignored = span3.makeCurrent()) {
            
            if (span3.isRecording()) {
                span3.setAttribute(NacosSemanticAttributes.FUNCTION_CURRENT_NAME,
                        "com.alibaba.nacos.client.config.NacosConfigService.getConfigInner()");
                span3.setAttribute(NacosSemanticAttributes.FUNCTION_CALLED_NAME,
                        "com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor.getSnapshot()");
                span3.setAttribute(NacosSemanticAttributes.AGENT_NAME, worker.getAgentName());
                span3.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span3.setAttribute(NacosSemanticAttributes.GROUP, group);
                span3.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                span3.setAttribute(NacosSemanticAttributes.NAMESPACE, namespace);
            }
            
            content = LocalConfigInfoProcessor.getSnapshot(worker.getAgentName(), dataId, group, tenant);
            
            if (content != null) {
                span3.setStatus(StatusCode.OK, "get snapshot ok");
                if (span3.isRecording()) {
                    span3.setAttribute(NacosSemanticAttributes.CONTENT, content);
                }
            } else {
                span3.setStatus(StatusCode.ERROR, "get snapshot failed");
            }
            
        } catch (Throwable e) {
            span3.recordException(e);
            span3.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span3.end();
        }
        
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get snapshot ok, dataId={}, group={}, tenant={}, config={}",
                    worker.getAgentName(), dataId, group, tenant, ContentUtils.truncateContent(content));
        }
        cr.setContent(content);
        
        String encryptedDataKey;
        Span span4 = ConfigTrace.getClientConfigServiceSpan("getEncryptDataKeySnapshot");
        try (Scope ignored = span4.makeCurrent()) {
            
            if (span4.isRecording()) {
                span4.setAttribute(NacosSemanticAttributes.FUNCTION_CURRENT_NAME,
                        "com.alibaba.nacos.client.config.NacosConfigService.getConfigInner()");
                span4.setAttribute(NacosSemanticAttributes.FUNCTION_CALLED_NAME,
                        "com.alibaba.nacos.client.config.impl.LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot()");
                span4.setAttribute(NacosSemanticAttributes.AGENT_NAME, worker.getAgentName());
                span4.setAttribute(NacosSemanticAttributes.DATA_ID, dataId);
                span4.setAttribute(NacosSemanticAttributes.GROUP, group);
                span4.setAttribute(NacosSemanticAttributes.TENANT, tenant);
                span4.setAttribute(NacosSemanticAttributes.NAMESPACE, namespace);
            }
            
            encryptedDataKey = LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(agent.getName(), dataId, group,
                    tenant);
            
        } catch (Throwable e) {
            span4.recordException(e);
            span4.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span4.end();
        }
        
        cr.setEncryptedDataKey(encryptedDataKey);
        configFilterChainManager.doFilter(null, cr);
        content = cr.getContent();
        return content;
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
