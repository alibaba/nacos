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
import com.alibaba.nacos.client.monitor.ConfigTrace;
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
        
        this.worker = new ClientWorker(this.configFilterChainManager, serverListManager, clientProperties);
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
        Span queryConfigSpan = ConfigTrace.getClientConfigServiceSpan("queryConfig");
        try (Scope ignored = queryConfigSpan.makeCurrent()) {
            
            configResponse = worker.getAgent()
                    .queryConfig(dataId, group, worker.getAgent().getTenant(), timeoutMs, false);
            
            if (queryConfigSpan.isRecording()) {
                queryConfigSpan.setAttribute("function.current.name",
                        "com.alibaba.nacos.client.config.NacosConfigService.getConfigAndSignListener()");
                queryConfigSpan.setAttribute("function.called.name",
                        "com.alibaba.nacos.client.config.impl.ClientWorker.ConfigRpcTransportClient.queryConfig()");
                queryConfigSpan.setAttribute("agent.name", worker.getAgentName());
                queryConfigSpan.setAttribute("data.id", dataId);
                queryConfigSpan.setAttribute("group", group);
                queryConfigSpan.setAttribute("tenant", worker.getAgent().getTenant());
                queryConfigSpan.setAttribute("timeout.ms", timeoutMs);
            }
            
        } catch (NacosException e) {
            queryConfigSpan.recordException(e);
            queryConfigSpan.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            queryConfigSpan.end();
        }
        
        String content = configResponse.getContent();
        String encryptedDataKey = configResponse.getEncryptedDataKey();
        
        Span addListenerSpan = ConfigTrace.getClientConfigServiceSpan("addTenantListeners");
        try (Scope ignored = addListenerSpan.makeCurrent()) {
            
            worker.addTenantListenersWithContent(dataId, group, content, encryptedDataKey, Arrays.asList(listener));
            
            if (addListenerSpan.isRecording()) {
                addListenerSpan.setAttribute("function.current.name",
                        "com.alibaba.nacos.client.config.NacosConfigService.getConfigAndSignListener()");
                addListenerSpan.setAttribute("function.called.name",
                        "com.alibaba.nacos.client.config.impl.ClientWorker.addTenantListenersWithContent()");
                addListenerSpan.setAttribute("agent.name", worker.getAgentName());
                addListenerSpan.setAttribute("data.id", dataId);
                addListenerSpan.setAttribute("group", group);
                addListenerSpan.setAttribute("server.response.content", content);
            }
            
        } catch (NacosException e) {
            addListenerSpan.recordException(e);
            addListenerSpan.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            addListenerSpan.end();
        }
        
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
        
        Span addListenerSpan = ConfigTrace.getClientConfigServiceSpan("addListener");
        try (Scope ignored = addListenerSpan.makeCurrent()) {
            
            worker.addTenantListeners(dataId, group, Arrays.asList(listener));
            
            if (addListenerSpan.isRecording()) {
                addListenerSpan.setAttribute("function.current.name",
                        "com.alibaba.nacos.client.config.NacosConfigService.addListener()");
                addListenerSpan.setAttribute("function.called.name",
                        "com.alibaba.nacos.client.config.impl.ClientWorker.addTenantListeners()");
                addListenerSpan.setAttribute("agent.name", worker.getAgentName());
                addListenerSpan.setAttribute("data.id", dataId);
                addListenerSpan.setAttribute("group", group);
            }
            
        } catch (NacosException e) {
            addListenerSpan.recordException(e);
            addListenerSpan.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            addListenerSpan.end();
        }
        
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
        Span removeListenerSpan = ConfigTrace.getClientConfigServiceSpan("removeListener");
        try (Scope ignored = removeListenerSpan.makeCurrent()) {
            
            worker.removeTenantListener(dataId, group, listener);
            
            if (removeListenerSpan.isRecording()) {
                removeListenerSpan.setAttribute("function.current.name",
                        "com.alibaba.nacos.client.config.NacosConfigService.removeListener()");
                removeListenerSpan.setAttribute("function.called.name",
                        "com.alibaba.nacos.client.config.impl.ClientWorker.removeTenantListener()");
                removeListenerSpan.setAttribute("agent.name", worker.getAgentName());
                removeListenerSpan.setAttribute("data.id", dataId);
                removeListenerSpan.setAttribute("group", group);
            }
            
        } catch (Throwable e) {
            removeListenerSpan.recordException(e);
            removeListenerSpan.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            removeListenerSpan.end();
        }
        
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
        
        Span getFailoverSpan = ConfigTrace.getClientConfigServiceSpan("getFailoverConfig");
        try (Scope ignored = getFailoverSpan.makeCurrent()) {
            
            content = LocalConfigInfoProcessor.getFailover(worker.getAgentName(), dataId, group, tenant);
            
            if (content != null) {
                getFailoverSpan.setStatus(StatusCode.OK, "get failover ok");
            } else {
                getFailoverSpan.setStatus(StatusCode.ERROR, "get failover failed");
            }
            
            if (getFailoverSpan.isRecording()) {
                getFailoverSpan.setAttribute("function.current.name",
                        "com.alibaba.nacos.client.config.NacosConfigService.getConfigInner()");
                getFailoverSpan.setAttribute("function.called.name",
                        "com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor.getFailover()");
                getFailoverSpan.setAttribute("agent.name", worker.getAgentName());
                getFailoverSpan.setAttribute("data.id", dataId);
                getFailoverSpan.setAttribute("group", group);
                getFailoverSpan.setAttribute("tenant", tenant);
            }
            
        } catch (Throwable e) {
            getFailoverSpan.recordException(e);
            getFailoverSpan.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            getFailoverSpan.end();
        }
        
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get failover ok, dataId={}, group={}, tenant={}, config={}",
                    worker.getAgentName(), dataId, group, tenant, ContentUtils.truncateContent(content));
            cr.setContent(content);
            String encryptedDataKey = LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(agent.getName(), dataId,
                    group, tenant);
            cr.setEncryptedDataKey(encryptedDataKey);
            configFilterChainManager.doFilter(null, cr);
            content = cr.getContent();
            return content;
        }
        
        try {
            
            ConfigResponse response;
            Span getServerConfigSpan = ConfigTrace.getClientConfigServiceSpan("getServerConfig");
            try (Scope ignored = getServerConfigSpan.makeCurrent()) {
                
                response = worker.getServerConfig(dataId, group, tenant, timeoutMs, false);
                
                if (getServerConfigSpan.isRecording()) {
                    getServerConfigSpan.setAttribute("function.current.name",
                            "com.alibaba.nacos.client.config.NacosConfigService.getConfigInner()");
                    getServerConfigSpan.setAttribute("function.called.name",
                            "com.alibaba.nacos.client.config.impl.ClientWorker.getServerConfig()");
                    getServerConfigSpan.setAttribute("agent.name", worker.getAgentName());
                    getServerConfigSpan.setAttribute("data.id", dataId);
                    getServerConfigSpan.setAttribute("group", group);
                    getServerConfigSpan.setAttribute("tenant", tenant);
                    getServerConfigSpan.setAttribute("timeout.ms", timeoutMs);
                }
                
            } catch (NacosException e) {
                getServerConfigSpan.recordException(e);
                getServerConfigSpan.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
                throw e;
            } finally {
                getServerConfigSpan.end();
            }
            
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
        
        Span getSnapshotSpan = ConfigTrace.getClientConfigServiceSpan("getSnapshotConfig");
        try (Scope ignored = getSnapshotSpan.makeCurrent()) {
            
            content = LocalConfigInfoProcessor.getSnapshot(worker.getAgentName(), dataId, group, tenant);
            
            if (content != null) {
                getFailoverSpan.setStatus(StatusCode.OK, "get snapshot ok");
            } else {
                getFailoverSpan.setStatus(StatusCode.ERROR, "get snapshot failed");
            }
            
            if (getSnapshotSpan.isRecording()) {
                getSnapshotSpan.setAttribute("function.current.name",
                        "com.alibaba.nacos.client.config.NacosConfigService.getConfigInner()");
                getSnapshotSpan.setAttribute("function.called.name",
                        "com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor.getSnapshot()");
                getSnapshotSpan.setAttribute("agent.name", worker.getAgentName());
                getSnapshotSpan.setAttribute("data.id", dataId);
                getSnapshotSpan.setAttribute("group", group);
                getSnapshotSpan.setAttribute("tenant", tenant);
            }
            
        } catch (Throwable e) {
            getSnapshotSpan.recordException(e);
            getSnapshotSpan.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            getSnapshotSpan.end();
        }
        
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get snapshot ok, dataId={}, group={}, tenant={}, config={}",
                    worker.getAgentName(), dataId, group, tenant, ContentUtils.truncateContent(content));
        }
        cr.setContent(content);
        String encryptedDataKey = LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(agent.getName(), dataId,
                group, tenant);
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
        
        boolean result;
        Span removeConfigSpan = ConfigTrace.getClientConfigServiceSpan("removeConfig");
        try (Scope ignored = removeConfigSpan.makeCurrent()) {
            
            result = worker.removeConfig(dataId, group, tenant, tag);
            
            if (result) {
                removeConfigSpan.setStatus(StatusCode.OK, "remove config success");
            } else {
                removeConfigSpan.setStatus(StatusCode.ERROR, "remove config failed");
            }
            
            if (removeConfigSpan.isRecording()) {
                removeConfigSpan.setAttribute("function.current.name",
                        "com.alibaba.nacos.client.config.NacosConfigService.removeConfigInner()");
                removeConfigSpan.setAttribute("function.called.name",
                        " com.alibaba.nacos.client.config.impl.ClientWorker.removeConfig()");
                removeConfigSpan.setAttribute("agent.name", worker.getAgentName());
                removeConfigSpan.setAttribute("data.id", dataId);
                removeConfigSpan.setAttribute("group", group);
                removeConfigSpan.setAttribute("tenant", tenant);
                removeConfigSpan.setAttribute("tag", tag);
            }
            
        } catch (Throwable e) {
            removeConfigSpan.recordException(e);
            removeConfigSpan.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            removeConfigSpan.end();
        }
        
        return result;
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
        
        boolean result;
        Span publishConfigSpan = ConfigTrace.getClientConfigServiceSpan("publishConfig");
        try (Scope ignored = publishConfigSpan.makeCurrent()) {
            
            result = worker.publishConfig(dataId, group, tenant, appName, tag, betaIps, content, encryptedDataKey,
                    casMd5, type);
            
            if (result) {
                publishConfigSpan.setStatus(StatusCode.OK, "publish config success");
            } else {
                publishConfigSpan.setStatus(StatusCode.ERROR, "publish config failed");
            }
            
            if (publishConfigSpan.isRecording()) {
                publishConfigSpan.setAttribute("function.current.name",
                        "com.alibaba.nacos.client.config.NacosConfigService.publishConfigInner()");
                publishConfigSpan.setAttribute("function.called.name",
                        " com.alibaba.nacos.client.config.impl.ClientWorker.publishConfig()");
                publishConfigSpan.setAttribute("agent.name", worker.getAgentName());
                publishConfigSpan.setAttribute("app.name", appName);
                publishConfigSpan.setAttribute("data.id", dataId);
                publishConfigSpan.setAttribute("group", group);
                publishConfigSpan.setAttribute("tenant", tenant);
                publishConfigSpan.setAttribute("tag", tag);
            }
            
        } catch (Throwable e) {
            publishConfigSpan.recordException(e);
            publishConfigSpan.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            publishConfigSpan.end();
        }
        
        return result;
    }
    
    @Override
    public String getServerStatus() {
        if (worker.isHealthServer()) {
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
