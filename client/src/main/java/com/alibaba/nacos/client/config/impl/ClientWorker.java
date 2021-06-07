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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ability.ClientAbilities;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ClientConfigMetricResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeNotifyResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.config.common.GroupKey;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.utils.ContentUtils;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.client.utils.EnvUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TenantUtil;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.ConnectionEventListener;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.nacos.api.common.Constants.ENCODE;

/**
 * Long polling.
 *
 * @author Nacos
 */
public class ClientWorker implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(ClientWorker.class);
    
    private static final String NOTIFY_HEADER = "notify";
    
    private static final String TAG_PARAM = "tag";
    
    private static final String APP_NAME_PARAM = "appName";
    
    private static final String BETAIPS_PARAM = "betaIps";
    
    private static final String TYPE_PARAM = "type";
    
    private static final String ENCRYPTED_DATA_KEY_PARAM = "encryptedDataKey";
    
    private static final String DEFAULT_RESOURCE = "";
    
    /**
     * groupKey -> cacheData.
     */
    private final AtomicReference<Map<String, CacheData>> cacheMap = new AtomicReference<Map<String, CacheData>>(
            new HashMap<String, CacheData>());
    
    private final ConfigFilterChainManager configFilterChainManager;
    
    private boolean isHealthServer = true;
    
    private String uuid = UUID.randomUUID().toString();
    
    private long timeout;
    
    private ConfigTransportClient agent;
    
    private int taskPenaltyTime;
    
    private boolean enableRemoteSyncConfig = false;
    
    /**
     * Add listeners for data.
     *
     * @param dataId    dataId of data
     * @param group     group of data
     * @param listeners listeners
     */
    public void addListeners(String dataId, String group, List<? extends Listener> listeners) {
        group = blank2defaultGroup(group);
        CacheData cache = addCacheDataIfAbsent(dataId, group);
        synchronized (cache) {
            
            for (Listener listener : listeners) {
                cache.addListener(listener);
            }
            cache.setSyncWithServer(false);
            agent.notifyListenConfig();
            
        }
    }
    
    /**
     * Add listeners for tenant.
     *
     * @param dataId    dataId of data
     * @param group     group of data
     * @param listeners listeners
     * @throws NacosException nacos exception
     */
    public void addTenantListeners(String dataId, String group, List<? extends Listener> listeners)
            throws NacosException {
        group = blank2defaultGroup(group);
        String tenant = agent.getTenant();
        CacheData cache = addCacheDataIfAbsent(dataId, group, tenant);
        synchronized (cache) {
            for (Listener listener : listeners) {
                cache.addListener(listener);
            }
            cache.setSyncWithServer(false);
            agent.notifyListenConfig();
        }
        
    }
    
    /**
     * Add listeners for tenant with content.
     *
     * @param dataId    dataId of data
     * @param group     group of data
     * @param content   content
     * @param listeners listeners
     * @throws NacosException nacos exception
     */
    public void addTenantListenersWithContent(String dataId, String group, String content,
            List<? extends Listener> listeners) throws NacosException {
        group = blank2defaultGroup(group);
        String tenant = agent.getTenant();
        CacheData cache = addCacheDataIfAbsent(dataId, group, tenant);
        synchronized (cache) {
            cache.setContent(content);
            for (Listener listener : listeners) {
                cache.addListener(listener);
            }
            cache.setSyncWithServer(false);
            agent.notifyListenConfig();
        }
        
    }
    
    /**
     * Remove listener.
     *
     * @param dataId   dataId of data
     * @param group    group of data
     * @param listener listener
     */
    public void removeListener(String dataId, String group, Listener listener) {
        group = blank2defaultGroup(group);
        CacheData cache = getCache(dataId, group);
        if (null != cache) {
            synchronized (cache) {
                cache.removeListener(listener);
                if (cache.getListeners().isEmpty()) {
                    cache.setSyncWithServer(false);
                    agent.removeCache(dataId, group);
                }
            }
            
        }
    }
    
    /**
     * Remove listeners for tenant.
     *
     * @param dataId   dataId of data
     * @param group    group of data
     * @param listener listener
     */
    public void removeTenantListener(String dataId, String group, Listener listener) {
        group = blank2defaultGroup(group);
        String tenant = agent.getTenant();
        CacheData cache = getCache(dataId, group, tenant);
        if (null != cache) {
            synchronized (cache) {
                cache.removeListener(listener);
                if (cache.getListeners().isEmpty()) {
                    cache.setSyncWithServer(false);
                    agent.removeCache(dataId, group);
                }
            }
        }
    }
    
    private void removeCache(String dataId, String group) {
        String groupKey = GroupKey.getKey(dataId, group);
        synchronized (cacheMap) {
            Map<String, CacheData> copy = new HashMap<String, CacheData>(cacheMap.get());
            copy.remove(groupKey);
            cacheMap.set(copy);
        }
        LOGGER.info("[{}] [unsubscribe] {}", this.agent.getName(), groupKey);
        
        MetricsMonitor.getListenConfigCountMonitor().set(cacheMap.get().size());
    }
    
    void removeCache(String dataId, String group, String tenant) {
        String groupKey = GroupKey.getKeyTenant(dataId, group, tenant);
        synchronized (cacheMap) {
            Map<String, CacheData> copy = new HashMap<String, CacheData>(cacheMap.get());
            copy.remove(groupKey);
            cacheMap.set(copy);
        }
        LOGGER.info("[{}] [unsubscribe] {}", agent.getName(), groupKey);
        
        MetricsMonitor.getListenConfigCountMonitor().set(cacheMap.get().size());
    }
    
    /**
     * remove config.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @param tag    tag.
     * @return success or not.
     * @throws NacosException exception to throw.
     */
    public boolean removeConfig(String dataId, String group, String tenant, String tag) throws NacosException {
        return agent.removeConfig(dataId, group, tenant, tag);
    }
    
    /**
     * publish config.
     *
     * @param dataId  dataId.
     * @param group   group.
     * @param tenant  tenant.
     * @param appName appName.
     * @param tag     tag.
     * @param betaIps betaIps.
     * @param content content.
     * @param casMd5  casMd5.
     * @param type    type.
     * @return success or not.
     * @throws NacosException exception throw.
     */
    public boolean publishConfig(String dataId, String group, String tenant, String appName, String tag, String betaIps,
            String content, String encryptedDataKey, String casMd5, String type) throws NacosException {
        return agent
                .publishConfig(dataId, group, tenant, appName, tag, betaIps, content, encryptedDataKey, casMd5, type);
    }
    
    /**
     * Add cache data if absent.
     *
     * @param dataId data id if data
     * @param group  group of data
     * @return cache data
     */
    public CacheData addCacheDataIfAbsent(String dataId, String group) {
        CacheData cache = getCache(dataId, group);
        if (null != cache) {
            return cache;
        }
        
        String key = GroupKey.getKey(dataId, group);
        cache = new CacheData(configFilterChainManager, agent.getName(), dataId, group);
        
        synchronized (cacheMap) {
            CacheData cacheFromMap = getCache(dataId, group);
            // multiple listeners on the same dataid+group and race condition,so double check again
            //other listener thread beat me to set to cacheMap
            if (null != cacheFromMap) {
                cache = cacheFromMap;
                //reset so that server not hang this check
                cache.setInitializing(true);
            } else {
                int taskId = cacheMap.get().size() / (int) ParamUtil.getPerTaskConfigSize();
                cache.setTaskId(taskId);
            }
            
            Map<String, CacheData> copy = new HashMap<String, CacheData>(cacheMap.get());
            copy.put(key, cache);
            cacheMap.set(copy);
        }
        
        LOGGER.info("[{}] [subscribe] {}", this.agent.getName(), key);
        
        MetricsMonitor.getListenConfigCountMonitor().set(cacheMap.get().size());
        
        return cache;
    }
    
    /**
     * Add cache data if absent.
     *
     * @param dataId data id if data
     * @param group  group of data
     * @param tenant tenant of data
     * @return cache data
     */
    public CacheData addCacheDataIfAbsent(String dataId, String group, String tenant) throws NacosException {
        CacheData cache = getCache(dataId, group, tenant);
        if (null != cache) {
            return cache;
        }
        String key = GroupKey.getKeyTenant(dataId, group, tenant);
        synchronized (cacheMap) {
            CacheData cacheFromMap = getCache(dataId, group, tenant);
            // multiple listeners on the same dataid+group and race condition,so
            // double check again
            // other listener thread beat me to set to cacheMap
            if (null != cacheFromMap) {
                cache = cacheFromMap;
                // reset so that server not hang this check
                cache.setInitializing(true);
            } else {
                cache = new CacheData(configFilterChainManager, agent.getName(), dataId, group, tenant);
                int taskId = cacheMap.get().size() / (int) ParamUtil.getPerTaskConfigSize();
                cache.setTaskId(taskId);
                // fix issue # 1317
                if (enableRemoteSyncConfig) {
                    ConfigResponse response = getServerConfig(dataId, group, tenant, 3000L, false);
                    cache.setContent(response.getContent());
                }
            }
            
            Map<String, CacheData> copy = new HashMap<String, CacheData>(this.cacheMap.get());
            copy.put(key, cache);
            cacheMap.set(copy);
        }
        LOGGER.info("[{}] [subscribe] {}", agent.getName(), key);
        
        MetricsMonitor.getListenConfigCountMonitor().set(cacheMap.get().size());
        
        return cache;
    }
    
    public CacheData getCache(String dataId, String group) {
        return getCache(dataId, group, TenantUtil.getUserTenantForAcm());
    }
    
    public CacheData getCache(String dataId, String group, String tenant) {
        if (null == dataId || null == group) {
            throw new IllegalArgumentException();
        }
        return cacheMap.get().get(GroupKey.getKeyTenant(dataId, group, tenant));
    }
    
    public ConfigResponse getServerConfig(String dataId, String group, String tenant, long readTimeout, boolean notify)
            throws NacosException {
        if (StringUtils.isBlank(group)) {
            group = Constants.DEFAULT_GROUP;
        }
        return this.agent.queryConfig(dataId, group, tenant, readTimeout, notify);
    }
    
    private void checkLocalConfig(String agentName, CacheData cacheData) {
        final String dataId = cacheData.dataId;
        final String group = cacheData.group;
        final String tenant = cacheData.tenant;
        File path = LocalConfigInfoProcessor.getFailoverFile(agentName, dataId, group, tenant);
        
        if (!cacheData.isUseLocalConfigInfo() && path.exists()) {
            String content = LocalConfigInfoProcessor.getFailover(agentName, dataId, group, tenant);
            final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
            cacheData.setUseLocalConfigInfo(true);
            cacheData.setLocalConfigInfoVersion(path.lastModified());
            cacheData.setContent(content);
            
            LOGGER.warn(
                    "[{}] [failover-change] failover file created. dataId={}, group={}, tenant={}, md5={}, content={}",
                    agentName, dataId, group, tenant, md5, ContentUtils.truncateContent(content));
            return;
        }
        
        // If use local config info, then it doesn't notify business listener and notify after getting from server.
        if (cacheData.isUseLocalConfigInfo() && !path.exists()) {
            cacheData.setUseLocalConfigInfo(false);
            LOGGER.warn("[{}] [failover-change] failover file deleted. dataId={}, group={}, tenant={}", agentName,
                    dataId, group, tenant);
            return;
        }
        
        // When it changed.
        if (cacheData.isUseLocalConfigInfo() && path.exists() && cacheData.getLocalConfigInfoVersion() != path
                .lastModified()) {
            String content = LocalConfigInfoProcessor.getFailover(agentName, dataId, group, tenant);
            final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
            cacheData.setUseLocalConfigInfo(true);
            cacheData.setLocalConfigInfoVersion(path.lastModified());
            cacheData.setContent(content);
            LOGGER.warn(
                    "[{}] [failover-change] failover file changed. dataId={}, group={}, tenant={}, md5={}, content={}",
                    agentName, dataId, group, tenant, md5, ContentUtils.truncateContent(content));
        }
    }
    
    private String blank2defaultGroup(String group) {
        return StringUtils.isBlank(group) ? Constants.DEFAULT_GROUP : group.trim();
    }
    
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public ClientWorker(final ConfigFilterChainManager configFilterChainManager, ServerListManager serverListManager,
            final Properties properties) throws NacosException {
        this.configFilterChainManager = configFilterChainManager;
        
        init(properties);
        
        agent = new ConfigRpcTransportClient(properties, serverListManager);
        
        ScheduledExecutorService executorService = Executors
                .newScheduledThreadPool(ThreadUtils.getSuitableThreadCount(1), r -> {
                    Thread t = new Thread(r);
                    t.setName("com.alibaba.nacos.client.Worker");
                    t.setDaemon(true);
                    return t;
                });
        agent.setExecutor(executorService);
        agent.start();
        
    }
    
    private void refreshContentAndCheck(String groupKey, boolean notify) {
        if (cacheMap.get() != null && cacheMap.get().containsKey(groupKey)) {
            CacheData cache = cacheMap.get().get(groupKey);
            refreshContentAndCheck(cache, notify);
        }
    }
    
    private void refreshContentAndCheck(CacheData cacheData, boolean notify) {
        try {
            ConfigResponse response = getServerConfig(cacheData.dataId, cacheData.group, cacheData.tenant, 3000L,
                    notify);
            cacheData.setContent(response.getContent());
            cacheData.setEncryptedDataKey(response.getEncryptedDataKey());
            if (null != response.getConfigType()) {
                cacheData.setType(response.getConfigType());
            }
            if (notify) {
                LOGGER.info("[{}] [data-received] dataId={}, group={}, tenant={}, md5={}, content={}, type={}",
                        agent.getName(), cacheData.dataId, cacheData.group, cacheData.tenant, cacheData.getMd5(),
                        ContentUtils.truncateContent(response.getContent()), response.getConfigType());
            }
            cacheData.checkListenerMd5();
        } catch (Exception e) {
            LOGGER.error("refresh content and check md5 fail ,dataId={},group={},tenant={} ", cacheData.dataId,
                    cacheData.group, cacheData.tenant, e);
        }
    }
    
    private void init(Properties properties) {
        
        timeout = Math.max(ConvertUtils.toInt(properties.getProperty(PropertyKeyConst.CONFIG_LONG_POLL_TIMEOUT),
                Constants.CONFIG_LONG_POLL_TIMEOUT), Constants.MIN_CONFIG_LONG_POLL_TIMEOUT);
        
        taskPenaltyTime = ConvertUtils
                .toInt(properties.getProperty(PropertyKeyConst.CONFIG_RETRY_TIME), Constants.CONFIG_RETRY_TIME);
        
        this.enableRemoteSyncConfig = Boolean
                .parseBoolean(properties.getProperty(PropertyKeyConst.ENABLE_REMOTE_SYNC_CONFIG));
    }
    
    private Map<String, Object> getMetrics(List<ClientConfigMetricRequest.MetricsKey> metricsKeys) {
        Map<String, Object> metric = new HashMap<>(16);
        metric.put("listenConfigSize", String.valueOf(this.cacheMap.get().size()));
        metric.put("clientVersion", VersionUtils.getFullClientVersion());
        metric.put("snapshotDir", LocalConfigInfoProcessor.LOCAL_SNAPSHOT_PATH);
        boolean isFixServer = agent.serverListManager.isFixed;
        metric.put("isFixedServer", isFixServer);
        metric.put("addressUrl", agent.serverListManager.addressServerUrl);
        metric.put("serverUrls", agent.serverListManager.getUrlString());
        
        Map<ClientConfigMetricRequest.MetricsKey, Object> metricValues = getMetricsValue(metricsKeys);
        metric.put("metricValues", metricValues);
        Map<String, Object> metrics = new HashMap<String, Object>(1);
        metrics.put(uuid, JacksonUtils.toJson(metric));
        return metrics;
    }
    
    private Map<ClientConfigMetricRequest.MetricsKey, Object> getMetricsValue(
            List<ClientConfigMetricRequest.MetricsKey> metricsKeys) {
        if (metricsKeys == null) {
            return null;
        }
        Map<ClientConfigMetricRequest.MetricsKey, Object> values = new HashMap<>(16);
        for (ClientConfigMetricRequest.MetricsKey metricsKey : metricsKeys) {
            if (ClientConfigMetricRequest.MetricsKey.CACHE_DATA.equals(metricsKey.getType())) {
                CacheData cacheData = cacheMap.get().get(metricsKey.getKey());
                values.putIfAbsent(metricsKey,
                        cacheData == null ? null : cacheData.getContent() + ":" + cacheData.getMd5());
            }
            if (ClientConfigMetricRequest.MetricsKey.SNAPSHOT_DATA.equals(metricsKey.getType())) {
                String[] configStr = GroupKey.parseKey(metricsKey.getKey());
                String snapshot = LocalConfigInfoProcessor
                        .getSnapshot(this.agent.getName(), configStr[0], configStr[1], configStr[2]);
                values.putIfAbsent(metricsKey,
                        snapshot == null ? null : snapshot + ":" + MD5Utils.md5Hex(snapshot, ENCODE));
            }
        }
        return values;
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        LOGGER.info("{} do shutdown begin", className);
        if (agent != null) {
            agent.shutdown();
        }
        LOGGER.info("{} do shutdown stop", className);
    }
    
    public boolean isHealthServer() {
        return isHealthServer;
    }
    
    private void setHealthServer(boolean isHealthServer) {
        this.isHealthServer = isHealthServer;
    }
    
    public class ConfigRpcTransportClient extends ConfigTransportClient {
        
        private final BlockingQueue<Object> listenExecutebell = new ArrayBlockingQueue<Object>(1);
        
        private Object bellItem = new Object();
        
        private long lastAllSyncTime = System.currentTimeMillis();
        
        /**
         * 5 minutes to check all listen cache keys.
         */
        private static final long ALL_SYNC_INTERNAL = 5 * 60 * 1000L;
        
        public ConfigRpcTransportClient(Properties properties, ServerListManager serverListManager) {
            super(properties, serverListManager);
        }
        
        private ConnectionType getConnectionType() {
            return ConnectionType.GRPC;
            
        }
        
        @Override
        public void shutdown() {
            synchronized (RpcClientFactory.getAllClientEntries()) {
                LOGGER.info("Trying to shutdown transport client " + this);
                Set<Map.Entry<String, RpcClient>> allClientEntries = RpcClientFactory.getAllClientEntries();
                Iterator<Map.Entry<String, RpcClient>> iterator = allClientEntries.iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, RpcClient> entry = iterator.next();
                    if (entry.getKey().startsWith(uuid)) {
                        LOGGER.info("Trying to shutdown rpc client " + entry.getKey());
                        
                        try {
                            entry.getValue().shutdown();
                        } catch (NacosException nacosException) {
                            nacosException.printStackTrace();
                        }
                        LOGGER.info("Remove rpc client " + entry.getKey());
                        iterator.remove();
                    }
                }
                
                LOGGER.info("Shutdown executor " + executor);
                executor.shutdown();
                Map<String, CacheData> stringCacheDataMap = cacheMap.get();
                for (Map.Entry<String, CacheData> entry : stringCacheDataMap.entrySet()) {
                    entry.getValue().setSyncWithServer(false);
                }
            }
            
        }
        
        private Map<String, String> getLabels() {
            
            Map<String, String> labels = new HashMap<String, String>(2, 1);
            labels.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
            labels.put(RemoteConstants.LABEL_MODULE, RemoteConstants.LABEL_MODULE_CONFIG);
            labels.put(Constants.APPNAME, AppNameUtils.getAppName());
            labels.put(Constants.VIPSERVER_TAG, EnvUtil.getSelfVipserverTag());
            labels.put(Constants.AMORY_TAG, EnvUtil.getSelfAmorayTag());
            labels.put(Constants.LOCATION_TAG, EnvUtil.getSelfLocationTag());
            
            return labels;
        }
        
        private void initRpcClientHandler(final RpcClient rpcClientInner) {
            /*
             * Register Config Change /Config ReSync Handler
             */
            rpcClientInner.registerServerRequestHandler((request) -> {
                if (request instanceof ConfigChangeNotifyRequest) {
                    ConfigChangeNotifyRequest configChangeNotifyRequest = (ConfigChangeNotifyRequest) request;
                    LOGGER.info("[{}] [server-push] config changed. dataId={}, group={},tenant={}",
                            rpcClientInner.getName(), configChangeNotifyRequest.getDataId(),
                            configChangeNotifyRequest.getGroup(), configChangeNotifyRequest.getTenant());
                    String groupKey = GroupKey
                            .getKeyTenant(configChangeNotifyRequest.getDataId(), configChangeNotifyRequest.getGroup(),
                                    configChangeNotifyRequest.getTenant());
                    
                    CacheData cacheData = cacheMap.get().get(groupKey);
                    if (cacheData != null) {
                        cacheData.setSyncWithServer(false);
                        notifyListenConfig();
                    }
                    return new ConfigChangeNotifyResponse();
                }
                return null;
            });
            
            rpcClientInner.registerServerRequestHandler((request) -> {
                if (request instanceof ClientConfigMetricRequest) {
                    ClientConfigMetricResponse response = new ClientConfigMetricResponse();
                    response.setMetrics(getMetrics(((ClientConfigMetricRequest) request).getMetricsKeys()));
                    return response;
                }
                return null;
            });
            
            rpcClientInner.registerConnectionListener(new ConnectionEventListener() {
                
                @Override
                public void onConnected() {
                    LOGGER.info("[{}] Connected,notify listen context...", rpcClientInner.getName());
                    notifyListenConfig();
                }
                
                @Override
                public void onDisConnect() {
                    String taskId = rpcClientInner.getLabels().get("taskId");
                    LOGGER.info("[{}] DisConnected,clear listen context...", rpcClientInner.getName());
                    Collection<CacheData> values = cacheMap.get().values();
                    
                    for (CacheData cacheData : values) {
                        if (StringUtils.isNotBlank(taskId)) {
                            if (Integer.valueOf(taskId).equals(cacheData.getTaskId())) {
                                cacheData.setSyncWithServer(false);
                            }
                        } else {
                            cacheData.setSyncWithServer(false);
                        }
                    }
                }
                
            });
            
            rpcClientInner.serverListFactory(new ServerListFactory() {
                @Override
                public String genNextServer() {
                    return ConfigRpcTransportClient.super.serverListManager.getNextServerAddr();
                    
                }
                
                @Override
                public String getCurrentServer() {
                    return ConfigRpcTransportClient.super.serverListManager.getCurrentServerAddr();
                    
                }
                
                @Override
                public List<String> getServerList() {
                    return ConfigRpcTransportClient.super.serverListManager.serverUrls;
                    
                }
            });
            
            NotifyCenter.registerSubscriber(new Subscriber() {
                @Override
                public void onEvent(Event event) {
                    rpcClientInner.onServerListChange();
                }
                
                @Override
                public Class<? extends Event> subscribeType() {
                    return ServerlistChangeEvent.class;
                }
            });
        }
        
        @Override
        public void startInternal() throws NacosException {
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    while (!executor.isShutdown() && !executor.isTerminated()) {
                        try {
                            listenExecutebell.poll(5L, TimeUnit.SECONDS);
                            if (executor.isShutdown() || executor.isTerminated()) {
                                continue;
                            }
                            executeConfigListen();
                        } catch (Exception e) {
                            LOGGER.error("[ rpc listen execute ] [rpc listen] exception", e);
                        }
                    }
                }
            }, 0L, TimeUnit.MILLISECONDS);
            
        }
        
        @Override
        public String getName() {
            return "config_rpc_client";
        }
        
        @Override
        public void notifyListenConfig() {
            listenExecutebell.offer(bellItem);
        }
        
        @Override
        public void executeConfigListen() {
            
            Map<String, List<CacheData>> listenCachesMap = new HashMap<String, List<CacheData>>(16);
            Map<String, List<CacheData>> removeListenCachesMap = new HashMap<String, List<CacheData>>(16);
            long now = System.currentTimeMillis();
            boolean needAllSync = now - lastAllSyncTime >= ALL_SYNC_INTERNAL;
            for (CacheData cache : cacheMap.get().values()) {
                
                synchronized (cache) {
                    
                    //check local listeners consistent.
                    if (cache.isSyncWithServer()) {
                        cache.checkListenerMd5();
                        if (!needAllSync) {
                            continue;
                        }
                    }
                    
                    if (!CollectionUtils.isEmpty(cache.getListeners())) {
                        //get listen  config
                        if (!cache.isUseLocalConfigInfo()) {
                            List<CacheData> cacheDatas = listenCachesMap.get(String.valueOf(cache.getTaskId()));
                            if (cacheDatas == null) {
                                cacheDatas = new LinkedList<CacheData>();
                                listenCachesMap.put(String.valueOf(cache.getTaskId()), cacheDatas);
                            }
                            cacheDatas.add(cache);
                            
                        }
                    } else if (CollectionUtils.isEmpty(cache.getListeners())) {
                        
                        if (!cache.isUseLocalConfigInfo()) {
                            List<CacheData> cacheDatas = removeListenCachesMap.get(String.valueOf(cache.getTaskId()));
                            if (cacheDatas == null) {
                                cacheDatas = new LinkedList<CacheData>();
                                removeListenCachesMap.put(String.valueOf(cache.getTaskId()), cacheDatas);
                            }
                            cacheDatas.add(cache);
                            
                        }
                    }
                }
                
            }
            
            boolean hasChangedKeys = false;
            
            if (!listenCachesMap.isEmpty()) {
                for (Map.Entry<String, List<CacheData>> entry : listenCachesMap.entrySet()) {
                    String taskId = entry.getKey();
                    List<CacheData> listenCaches = entry.getValue();
                    
                    ConfigBatchListenRequest configChangeListenRequest = buildConfigRequest(listenCaches);
                    configChangeListenRequest.setListen(true);
                    try {
                        RpcClient rpcClient = ensureRpcClient(taskId);
                        ConfigChangeBatchListenResponse configChangeBatchListenResponse = (ConfigChangeBatchListenResponse) requestProxy(
                                rpcClient, configChangeListenRequest);
                        if (configChangeBatchListenResponse != null && configChangeBatchListenResponse.isSuccess()) {
                            
                            Set<String> changeKeys = new HashSet<String>();
                            //handle changed keys,notify listener
                            if (!CollectionUtils.isEmpty(configChangeBatchListenResponse.getChangedConfigs())) {
                                hasChangedKeys = true;
                                for (ConfigChangeBatchListenResponse.ConfigContext changeConfig : configChangeBatchListenResponse
                                        .getChangedConfigs()) {
                                    String changeKey = GroupKey
                                            .getKeyTenant(changeConfig.getDataId(), changeConfig.getGroup(),
                                                    changeConfig.getTenant());
                                    changeKeys.add(changeKey);
                                    boolean isInitializing = cacheMap.get().get(changeKey).isInitializing();
                                    refreshContentAndCheck(changeKey, !isInitializing);
                                }
                                
                            }
                            
                            //handler content configs
                            for (CacheData cacheData : listenCaches) {
                                String groupKey = GroupKey
                                        .getKeyTenant(cacheData.dataId, cacheData.group, cacheData.getTenant());
                                if (!changeKeys.contains(groupKey)) {
                                    //sync:cache data md5 = server md5 && cache data md5 = all listeners md5.
                                    synchronized (cacheData) {
                                        if (!cacheData.getListeners().isEmpty()) {
                                            cacheData.setSyncWithServer(true);
                                            continue;
                                        }
                                    }
                                }
                                
                                cacheData.setInitializing(false);
                            }
                            
                        }
                    } catch (Exception e) {
                        
                        LOGGER.error("Async listen config change error ", e);
                        try {
                            Thread.sleep(50L);
                        } catch (InterruptedException interruptedException) {
                            //ignore
                        }
                    }
                }
            }
            
            if (!removeListenCachesMap.isEmpty()) {
                for (Map.Entry<String, List<CacheData>> entry : removeListenCachesMap.entrySet()) {
                    String taskId = entry.getKey();
                    List<CacheData> removeListenCaches = entry.getValue();
                    ConfigBatchListenRequest configChangeListenRequest = buildConfigRequest(removeListenCaches);
                    configChangeListenRequest.setListen(false);
                    try {
                        RpcClient rpcClient = ensureRpcClient(taskId);
                        boolean removeSuccess = unListenConfigChange(rpcClient, configChangeListenRequest);
                        if (removeSuccess) {
                            for (CacheData cacheData : removeListenCaches) {
                                synchronized (cacheData) {
                                    if (cacheData.getListeners().isEmpty()) {
                                        ClientWorker.this
                                                .removeCache(cacheData.dataId, cacheData.group, cacheData.tenant);
                                    }
                                }
                            }
                        }
                        
                    } catch (Exception e) {
                        LOGGER.error("async remove listen config change error ", e);
                    }
                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException interruptedException) {
                        //ignore
                    }
                }
            }
            
            if (needAllSync) {
                lastAllSyncTime = now;
            }
            //If has changed keys,notify re sync md5.
            if (hasChangedKeys) {
                notifyListenConfig();
            }
        }
        
        private RpcClient ensureRpcClient(String taskId) throws NacosException {
            synchronized (ClientWorker.this) {
                
                Map<String, String> labels = getLabels();
                Map<String, String> newLabels = new HashMap<String, String>(labels);
                newLabels.put("taskId", taskId);
                
                RpcClient rpcClient = RpcClientFactory
                        .createClient(uuid + "_config-" + taskId, getConnectionType(), newLabels);
                if (rpcClient.isWaitInitiated()) {
                    initRpcClientHandler(rpcClient);
                    rpcClient.setTenant(getTenant());
                    rpcClient.clientAbilities(initAbilities());
                    rpcClient.start();
                }
                
                return rpcClient;
            }
            
        }
        
        private ClientAbilities initAbilities() {
            ClientAbilities clientAbilities = new ClientAbilities();
            clientAbilities.getRemoteAbility().setSupportRemoteConnection(true);
            clientAbilities.getConfigAbility().setSupportRemoteMetrics(true);
            return clientAbilities;
        }
        
        /**
         * build config string.
         *
         * @param caches caches to build config string.
         * @return request.
         */
        private ConfigBatchListenRequest buildConfigRequest(List<CacheData> caches) {
            
            ConfigBatchListenRequest configChangeListenRequest = new ConfigBatchListenRequest();
            for (CacheData cacheData : caches) {
                configChangeListenRequest.addConfigListenContext(cacheData.group, cacheData.dataId, cacheData.tenant,
                        cacheData.getMd5());
            }
            return configChangeListenRequest;
        }
        
        @Override
        public void removeCache(String dataId, String group) {
            // Notify to rpc un listen ,and remove cache if success.
            notifyListenConfig();
        }
        
        /**
         * send cancel listen config change request .
         *
         * @param configChangeListenRequest request of remove listen config string.
         */
        private boolean unListenConfigChange(RpcClient rpcClient, ConfigBatchListenRequest configChangeListenRequest)
                throws NacosException {
            
            ConfigChangeBatchListenResponse response = (ConfigChangeBatchListenResponse) requestProxy(rpcClient,
                    configChangeListenRequest);
            return response.isSuccess();
        }
        
        @Override
        public ConfigResponse queryConfig(String dataId, String group, String tenant, long readTimeouts, boolean notify)
                throws NacosException {
            ConfigQueryRequest request = ConfigQueryRequest.build(dataId, group, tenant);
            request.putHeader(NOTIFY_HEADER, String.valueOf(notify));
            RpcClient rpcClient = getOneRunningClient();
            if (notify) {
                CacheData cacheData = cacheMap.get().get(GroupKey.getKeyTenant(dataId, group, tenant));
                if (cacheData != null) {
                    rpcClient = ensureRpcClient(String.valueOf(cacheData.getTaskId()));
                }
            }
            ConfigQueryResponse response = (ConfigQueryResponse) requestProxy(rpcClient, request, readTimeouts);
            
            ConfigResponse configResponse = new ConfigResponse();
            if (response.isSuccess()) {
                LocalConfigInfoProcessor.saveSnapshot(this.getName(), dataId, group, tenant, response.getContent());
                configResponse.setContent(response.getContent());
                String configType;
                if (StringUtils.isNotBlank(response.getContentType())) {
                    configType = response.getContentType();
                } else {
                    configType = ConfigType.TEXT.getType();
                }
                configResponse.setConfigType(configType);
                String encryptedDataKey = response.getEncryptedDataKey();
                LocalEncryptedDataKeyProcessor
                        .saveEncryptDataKeySnapshot(agent.getName(), dataId, group, tenant, encryptedDataKey);
                configResponse.setEncryptedDataKey(encryptedDataKey);
                return configResponse;
            } else if (response.getErrorCode() == ConfigQueryResponse.CONFIG_NOT_FOUND) {
                LocalConfigInfoProcessor.saveSnapshot(this.getName(), dataId, group, tenant, null);
                LocalEncryptedDataKeyProcessor.saveEncryptDataKeySnapshot(agent.getName(), dataId, group, tenant, null);
                return configResponse;
            } else if (response.getErrorCode() == ConfigQueryResponse.CONFIG_QUERY_CONFLICT) {
                LOGGER.error(
                        "[{}] [sub-server-error] get server config being modified concurrently, dataId={}, group={}, "
                                + "tenant={}", this.getName(), dataId, group, tenant);
                throw new NacosException(NacosException.CONFLICT,
                        "data being modified, dataId=" + dataId + ",group=" + group + ",tenant=" + tenant);
            } else {
                LOGGER.error("[{}] [sub-server-error]  dataId={}, group={}, tenant={}, code={}", this.getName(), dataId,
                        group, tenant, response);
                throw new NacosException(response.getErrorCode(),
                        "http error, code=" + response.getErrorCode() + ",dataId=" + dataId + ",group=" + group
                                + ",tenant=" + tenant);
                
            }
        }
        
        private Response requestProxy(RpcClient rpcClientInner, Request request) throws NacosException {
            return requestProxy(rpcClientInner, request, 3000L);
        }
        
        private Response requestProxy(RpcClient rpcClientInner, Request request, long timeoutMills)
                throws NacosException {
            try {
                request.putAllHeader(super.getSecurityHeaders());
                request.putAllHeader(super.getSpasHeaders());
                request.putAllHeader(super.getCommonHeader());
            } catch (Exception e) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, e);
            }
            
            Map<String, String> signHeaders = SpasAdapter.getSignHeaders(resourceBuild(request), secretKey);
            if (signHeaders != null && !signHeaders.isEmpty()) {
                request.putAllHeader(signHeaders);
            }
            JsonObject asJsonObjectTemp = new Gson().toJsonTree(request).getAsJsonObject();
            asJsonObjectTemp.remove("headers");
            asJsonObjectTemp.remove("requestId");
            boolean limit = Limiter.isLimit(request.getClass() + asJsonObjectTemp.toString());
            if (limit) {
                throw new NacosException(NacosException.CLIENT_OVER_THRESHOLD,
                        "More than client-side current limit threshold");
            }
            return rpcClientInner.request(request, timeoutMills);
        }
        
        private String resourceBuild(Request request) {
            if (request instanceof ConfigQueryRequest) {
                String tenant = ((ConfigQueryRequest) request).getTenant();
                String group = ((ConfigQueryRequest) request).getGroup();
                return getResource(tenant, group);
            }
            if (request instanceof ConfigPublishRequest) {
                String tenant = ((ConfigPublishRequest) request).getTenant();
                String group = ((ConfigPublishRequest) request).getGroup();
                return getResource(tenant, group);
            }
            
            if (request instanceof ConfigRemoveRequest) {
                String tenant = ((ConfigRemoveRequest) request).getTenant();
                String group = ((ConfigRemoveRequest) request).getGroup();
                return getResource(tenant, group);
            }
            return DEFAULT_RESOURCE;
        }
        
        private String getResource(String tenant, String group) {
            if (StringUtils.isNotBlank(tenant) && StringUtils.isNotBlank(group)) {
                return tenant + "+" + group;
            }
            if (StringUtils.isNotBlank(group)) {
                return group;
            }
            if (StringUtils.isNotBlank(tenant)) {
                return tenant;
            }
            return DEFAULT_RESOURCE;
        }
        
        RpcClient getOneRunningClient() throws NacosException {
            return ensureRpcClient("0");
        }
        
        @Override
        public boolean publishConfig(String dataId, String group, String tenant, String appName, String tag,
                String betaIps, String content, String encryptedDataKey, String casMd5, String type)
                throws NacosException {
            try {
                ConfigPublishRequest request = new ConfigPublishRequest(dataId, group, tenant, content);
                request.setCasMd5(casMd5);
                request.putAdditionalParam(TAG_PARAM, tag);
                request.putAdditionalParam(APP_NAME_PARAM, appName);
                request.putAdditionalParam(BETAIPS_PARAM, betaIps);
                request.putAdditionalParam(TYPE_PARAM, type);
                request.putAdditionalParam(ENCRYPTED_DATA_KEY_PARAM, encryptedDataKey);
                ConfigPublishResponse response = (ConfigPublishResponse) requestProxy(getOneRunningClient(), request);
                if (!response.isSuccess()) {
                    LOGGER.warn("[{}] [publish-single] fail, dataId={}, group={}, tenant={}, code={}, msg={}",
                            this.getName(), dataId, group, tenant, response.getErrorCode(), response.getMessage());
                    return false;
                } else {
                    LOGGER.info("[{}] [publish-single] ok, dataId={}, group={}, tenant={}, config={}", getName(),
                            dataId, group, tenant, ContentUtils.truncateContent(content));
                    return true;
                }
            } catch (Exception e) {
                LOGGER.warn("[{}] [publish-single] error, dataId={}, group={}, tenant={}, code={}, msg={}",
                        this.getName(), dataId, group, tenant, "unkonw", e.getMessage());
                return false;
            }
        }
        
        @Override
        public boolean removeConfig(String dataId, String group, String tenant, String tag) throws NacosException {
            ConfigRemoveRequest request = new ConfigRemoveRequest(dataId, group, tenant, tag);
            ConfigRemoveResponse response = (ConfigRemoveResponse) requestProxy(getOneRunningClient(), request);
            return response.isSuccess();
        }
    }
    
    public String getAgentName() {
        return this.agent.getName();
    }
}
