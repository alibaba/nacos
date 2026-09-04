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
import com.alibaba.nacos.api.config.ConfigQueryResult;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.GetConfigRequest;
import com.alibaba.nacos.api.config.PublishConfigRequest;
import com.alibaba.nacos.api.config.PublishConfigResult;
import com.alibaba.nacos.api.config.RemoveConfigRequest;
import com.alibaba.nacos.api.config.RemoveConfigResult;
import com.alibaba.nacos.api.config.filter.IConfigFilter;
import com.alibaba.nacos.api.config.listener.FuzzyWatchEventWatcher;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigRequest;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ConfigFuzzyWatchContext;
import com.alibaba.nacos.client.config.impl.ConfigServerListManager;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.alibaba.nacos.client.config.impl.LocalEncryptedDataKeyProcessor;
import com.alibaba.nacos.client.config.utils.ParamUtils;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ClientBasicParamUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.PreInitUtils;
import com.alibaba.nacos.client.utils.ValidatorUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;

import static com.alibaba.nacos.api.common.Constants.ALL_PATTERN;

/**
 * Config Impl.
 *
 * @author Nacos
 */
public class NacosConfigService implements ConfigService {
    
    private static final Logger LOGGER = LogUtils.logger(NacosConfigService.class);
    
    private static final String UP = "UP";
    
    private static final String DOWN = "DOWN";
    
    /**
     * long polling.
     */
    private final ClientWorker worker;
    
    private String namespace;
    
    private final ConfigFilterChainManager configFilterChainManager;
    
    public NacosConfigService(Properties properties) throws NacosException {
        PreInitUtils.asyncPreLoadCostComponent();
        final NacosClientProperties clientProperties =
            NacosClientProperties.PROTOTYPE.derive(properties);
        LOGGER.info(ClientBasicParamUtil.getInputParameters(clientProperties.asProperties()));
        ValidatorUtils.checkInitParam(clientProperties);
        
        initNamespace(clientProperties);
        this.configFilterChainManager =
            new ConfigFilterChainManager(clientProperties.asProperties());
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        
        this.worker = new ClientWorker(this.configFilterChainManager, serverListManager,
            clientProperties);
        
    }
    
    private void initNamespace(NacosClientProperties properties) {
        namespace = ClientBasicParamUtil.parseNamespace(properties);
        properties.setProperty(PropertyKeyConst.NAMESPACE, namespace);
    }
    
    @Override
    public String getConfig(String dataId, String group, long timeoutMs) throws NacosException {
        return getConfigInner(namespace, dataId, group, timeoutMs);
    }
    
    @Override
    public ConfigQueryResult getConfig(GetConfigRequest request) throws NacosException {
        String dataId = request.getDataId();
        String group = request.getGroup();
        long timeoutMs = request.getTimeoutMs();
        String localMd5 = request.getLocalMd5();
        
        // If localMd5 is not explicitly provided, try to compute it from local snapshot
        if (StringUtils.isBlank(localMd5)) {
            localMd5 = resolveLocalMd5(dataId, group);
        }
        
        ConfigResponse response = getConfigInnerWithResponse(namespace, dataId, group, timeoutMs,
            localMd5);
        
        ConfigQueryResult result = new ConfigQueryResult();
        result.setContent(response.getContent());
        result.setMd5(response.getMd5());
        result.setConfigType(response.getConfigType());
        result.setEncryptedDataKey(response.getEncryptedDataKey());
        return result;
    }
    
    /**
     * Resolve local MD5 from snapshot or cache for 304 conditional GET.
     *
     * <p>First checks the in-memory CacheData (if the config is being listened to),
     * then falls back to the local snapshot file.</p>
     *
     * @param dataId dataId
     * @param group  group
     * @return local MD5, or null if no local cache exists
     */
    private String resolveLocalMd5(String dataId, String group) {
        group = blank2defaultGroup(group);
        // Try in-memory cache first
        try {
            com.alibaba.nacos.client.config.impl.CacheData cacheData =
                worker.getCache(dataId, group, namespace);
            if (cacheData != null && StringUtils.isNotBlank(cacheData.getMd5())) {
                return cacheData.getMd5();
            }
        } catch (Exception e) {
            // ignore, fall through to snapshot
        }
        // Try local snapshot
        try {
            String snapshotContent =
                LocalConfigInfoProcessor.getSnapshot(worker.getAgentName(), dataId, group,
                    namespace);
            if (StringUtils.isNotBlank(snapshotContent)) {
                return MD5Utils.md5Hex(snapshotContent, Constants.ENCODE);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
    
    @Override
    public String getConfigAndSignListener(String dataId, String group, long timeoutMs,
        Listener listener)
        throws NacosException {
        group = StringUtils.isBlank(group) ? Constants.DEFAULT_GROUP : group.trim();
        ConfigResponse configResponse = worker.getAgent()
            .queryConfig(dataId, group, worker.getAgent().getTenant(), timeoutMs, false);
        String content = configResponse.getContent();
        String encryptedDataKey = configResponse.getEncryptedDataKey();
        worker.addTenantListenersWithContent(dataId, group, content, encryptedDataKey,
            Collections.singletonList(listener));
        
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
        worker.addTenantListeners(dataId, group, Collections.singletonList(listener));
    }
    
    @Override
    public void fuzzyWatch(String groupNamePattern, FuzzyWatchEventWatcher watcher)
        throws NacosException {
        doAddFuzzyWatch(ALL_PATTERN, groupNamePattern, watcher);
    }
    
    @Override
    public void fuzzyWatch(String dataIdPattern, String groupNamePattern,
        FuzzyWatchEventWatcher watcher)
        throws NacosException {
        doAddFuzzyWatch(dataIdPattern, groupNamePattern, watcher);
    }
    
    @Override
    public Future<Set<String>> fuzzyWatchWithGroupKeys(String groupNamePattern,
        FuzzyWatchEventWatcher watcher)
        throws NacosException {
        return doAddFuzzyWatch(ALL_PATTERN, groupNamePattern, watcher);
    }
    
    @Override
    public Future<Set<String>> fuzzyWatchWithGroupKeys(String dataIdPattern,
        String groupNamePattern,
        FuzzyWatchEventWatcher watcher) throws NacosException {
        return doAddFuzzyWatch(dataIdPattern, groupNamePattern, watcher);
    }
    
    private Future<Set<String>> doAddFuzzyWatch(String dataIdPattern, String groupNamePattern,
        FuzzyWatchEventWatcher watcher) throws NacosException {
        ConfigFuzzyWatchContext configFuzzyWatchContext =
            worker.addTenantFuzzyWatcher(dataIdPattern, groupNamePattern,
                watcher);
        return configFuzzyWatchContext.createNewFuture();
    }
    
    @Override
    public void cancelFuzzyWatch(String groupNamePattern, FuzzyWatchEventWatcher watcher)
        throws NacosException {
        cancelFuzzyWatch(ALL_PATTERN, groupNamePattern, watcher);
    }
    
    @Override
    public void cancelFuzzyWatch(String dataIdPattern, String groupNamePattern,
        FuzzyWatchEventWatcher watcher)
        throws NacosException {
        doCancelFuzzyWatch(dataIdPattern, groupNamePattern, watcher);
    }
    
    private void doCancelFuzzyWatch(String dataIdPattern, String groupNamePattern,
        FuzzyWatchEventWatcher watcher)
        throws NacosException {
        if (null == watcher) {
            return;
        }
        worker.removeFuzzyListenListener(dataIdPattern, groupNamePattern, watcher);
    }
    
    @Override
    public boolean publishConfig(String dataId, String group, String content)
        throws NacosException {
        return publishConfig(dataId, group, content, ConfigType.getDefaultType().getType());
    }
    
    @Override
    public boolean publishConfig(String dataId, String group, String content, String type)
        throws NacosException {
        PublishConfigRequest request = PublishConfigRequest.builder()
            .dataId(dataId)
            .group(group)
            .content(content)
            .type(type)
            .build();
        return publishConfig(request).isSuccess();
    }
    
    @Override
    public PublishConfigResult publishConfig(PublishConfigRequest request)
        throws NacosException {
        String dataId = request.getDataId();
        String group = request.getGroup();
        String content = request.getContent();
        String type = request.getType() != null ? request.getType()
            : ConfigType.getDefaultType().getType();
        String casMd5 = request.getCasMd5();
        
        group = blank2defaultGroup(group);
        ParamUtils.checkParam(dataId, group, content);
        
        ConfigRequest cr = new ConfigRequest();
        cr.setDataId(dataId);
        cr.setTenant(namespace);
        cr.setGroup(group);
        cr.setContent(content);
        cr.setType(type);
        configFilterChainManager.doFilter(cr, null);
        content = cr.getContent();
        String encryptedDataKey = cr.getEncryptedDataKey();
        
        boolean success = worker.publishConfig(dataId, group, namespace, null, null, null,
            content, encryptedDataKey, casMd5, type);
        
        if (success) {
            // Compute MD5 of published content for the result
            String publishedMd5 = MD5Utils.md5Hex(content, Constants.ENCODE);
            return PublishConfigResult.success(publishedMd5);
        }
        return PublishConfigResult.fail(-1, "publish config failed");
    }
    
    @Override
    public boolean publishConfigCas(String dataId, String group, String content, String casMd5)
        throws NacosException {
        return publishConfigCas(dataId, group, content, casMd5,
            ConfigType.getDefaultType().getType());
    }
    
    @Override
    public boolean publishConfigCas(String dataId, String group, String content, String casMd5,
        String type)
        throws NacosException {
        PublishConfigRequest request = PublishConfigRequest.builder()
            .dataId(dataId)
            .group(group)
            .content(content)
            .type(type)
            .casMd5(casMd5)
            .build();
        return publishConfig(request).isSuccess();
    }
    
    @Override
    public boolean removeConfig(String dataId, String group) throws NacosException {
        RemoveConfigRequest request = RemoveConfigRequest.builder()
            .dataId(dataId)
            .group(group)
            .build();
        return removeConfig(request).isSuccess();
    }
    
    @Override
    public RemoveConfigResult removeConfig(RemoveConfigRequest request) throws NacosException {
        String dataId = request.getDataId();
        String group = request.getGroup();
        group = blank2defaultGroup(group);
        ParamUtils.checkKeyParam(dataId, group);
        boolean success = worker.removeConfig(dataId, group, namespace, null);
        return success ? RemoveConfigResult.success()
            : RemoveConfigResult.fail(-1, "remove config failed");
    }
    
    @Override
    public void removeListener(String dataId, String group, Listener listener) {
        worker.removeTenantListener(dataId, group, listener);
    }
    
    private String getConfigInner(String tenant, String dataId, String group, long timeoutMs)
        throws NacosException {
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
        String content =
            LocalConfigInfoProcessor.getFailover(worker.getAgentName(), dataId, group, tenant);
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get failover ok, dataId={}, group={}, tenant={}",
                worker.getAgentName(),
                dataId, group, tenant);
            cr.setContent(content);
            String encryptedDataKey =
                LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(worker.getAgentName(),
                    dataId, group, tenant);
            cr.setEncryptedDataKey(encryptedDataKey);
            configFilterChainManager.doFilter(null, cr);
            content = cr.getContent();
            return content;
        }
        
        try {
            ConfigResponse response =
                worker.getServerConfig(dataId, group, tenant, timeoutMs, false);
            cr.setContent(response.getContent());
            cr.setEncryptedDataKey(response.getEncryptedDataKey());
            configFilterChainManager.doFilter(null, cr);
            content = cr.getContent();
            
            return content;
        } catch (NacosException ioe) {
            if (NacosException.NO_RIGHT == ioe.getErrCode()) {
                throw ioe;
            }
            LOGGER.warn(
                "[{}] [get-config] get from server error, dataId={}, group={}, tenant={}, msg={}",
                worker.getAgentName(), dataId, group, tenant, ioe.toString());
        }
        
        content =
            LocalConfigInfoProcessor.getSnapshot(worker.getAgentName(), dataId, group, tenant);
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get snapshot ok, dataId={}, group={}, tenant={}",
                worker.getAgentName(),
                dataId, group, tenant);
        }
        cr.setContent(content);
        String encryptedDataKey =
            LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(worker.getAgentName(),
                dataId, group, tenant);
        cr.setEncryptedDataKey(encryptedDataKey);
        configFilterChainManager.doFilter(null, cr);
        content = cr.getContent();
        return content;
    }
    
    private String blank2defaultGroup(String group) {
        return (StringUtils.isBlank(group)) ? Constants.DEFAULT_GROUP : group.trim();
    }
    
    private ConfigResponse getConfigInnerWithResponse(String tenant, String dataId, String group,
        long timeoutMs)
        throws NacosException {
        return getConfigInnerWithResponse(tenant, dataId, group, timeoutMs, null);
    }
    
    /**
     * Get config inner with response, supporting 304 conditional GET via localMd5.
     *
     * @param tenant    tenant
     * @param dataId    dataId
     * @param group     group
     * @param timeoutMs timeout in milliseconds
     * @param localMd5  local cached MD5 for 304 conditional GET
     * @return config response
     * @throws NacosException nacos exception
     * @since 3.3.0
     */
    private ConfigResponse getConfigInnerWithResponse(String tenant, String dataId, String group,
        long timeoutMs, String localMd5)
        throws NacosException {
        group = blank2defaultGroup(group);
        ParamUtils.checkKeyParam(dataId, group);
        ConfigResponse cr = new ConfigResponse();
        
        cr.setDataId(dataId);
        cr.setTenant(tenant);
        cr.setGroup(group);
        
        // Try local failover first
        String content =
            LocalConfigInfoProcessor.getFailover(worker.getAgentName(), dataId, group, tenant);
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get failover ok, dataId={}, group={}, tenant={}",
                worker.getAgentName(),
                dataId, group, tenant);
            cr.setContent(content);
            String encryptedDataKey =
                LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(worker.getAgentName(),
                    dataId, group, tenant);
            cr.setEncryptedDataKey(encryptedDataKey);
            // Failover doesn't have MD5 from server
            configFilterChainManager.doFilter(null, cr);
            return cr;
        }
        
        try {
            ConfigResponse response =
                worker.getServerConfig(dataId, group, tenant, timeoutMs, false, localMd5);
            cr.setContent(response.getContent());
            cr.setMd5(response.getMd5());
            cr.setEncryptedDataKey(response.getEncryptedDataKey());
            cr.setConfigType(response.getConfigType());
            configFilterChainManager.doFilter(null, cr);
            return cr;
        } catch (NacosException ioe) {
            if (NacosException.NO_RIGHT == ioe.getErrCode()) {
                throw ioe;
            }
            LOGGER.warn(
                "[{}] [get-config] get from server error, dataId={}, group={}, tenant={}, msg={}",
                worker.getAgentName(), dataId, group, tenant, ioe.toString());
        }
        
        // Fall back to snapshot
        content =
            LocalConfigInfoProcessor.getSnapshot(worker.getAgentName(), dataId, group, tenant);
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get snapshot ok, dataId={}, group={}, tenant={}",
                worker.getAgentName(),
                dataId, group, tenant);
        }
        cr.setContent(content);
        String encryptedDataKey =
            LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(worker.getAgentName(),
                dataId, group, tenant);
        cr.setEncryptedDataKey(encryptedDataKey);
        // Snapshot doesn't have MD5 from server
        configFilterChainManager.doFilter(null, cr);
        return cr;
    }
    
    @Override
    public ConfigQueryResult getConfigWithResult(String dataId, String group, long timeoutMs)
        throws NacosException {
        ConfigResponse response = getConfigInnerWithResponse(namespace, dataId, group, timeoutMs);
        ConfigQueryResult result = new ConfigQueryResult();
        result.setContent(response.getContent());
        result.setMd5(response.getMd5());
        result.setConfigType(response.getConfigType());
        result.setEncryptedDataKey(response.getEncryptedDataKey());
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
