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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeNotifyResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigPubishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.config.common.GroupKey;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigRequest;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.http.MetricsHttpAgent;
import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.client.config.utils.ContentUtils;
import com.alibaba.nacos.client.config.utils.ParamUtils;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TenantUtil;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.ConnectionEventListener;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.nacos.api.common.Constants.CONFIG_TYPE;
import static com.alibaba.nacos.api.common.Constants.LINE_SEPARATOR;
import static com.alibaba.nacos.api.common.Constants.WORD_SEPARATOR;

/**
 * Long polling.
 *
 * @author Nacos
 */
public class ClientWorker implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(ClientWorker.class);
    
    /**
     * Add listeners for data.
     *
     * @param dataId    dataId of data
     * @param group     group of data
     * @param listeners listeners
     */
    public void addListeners(String dataId, String group, List<? extends Listener> listeners) {
        group = null2defaultGroup(group);
        CacheData cache = addCacheDataIfAbsent(dataId, group);
        for (Listener listener : listeners) {
            cache.addListener(listener);
        }
        if (!cache.isListenSuccess()) {
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
        group = null2defaultGroup(group);
        CacheData cache = getCache(dataId, group);
        if (null != cache) {
            cache.removeListener(listener);
            if (cache.getListeners().isEmpty()) {
                agent.removeCache(dataId, group);
            }
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
        group = null2defaultGroup(group);
        String tenant = agent.getTenant();
        CacheData cache = addCacheDataIfAbsent(dataId, group, tenant);
        for (Listener listener : listeners) {
            cache.addListener(listener);
        }
        if (!cache.isListenSuccess()) {
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
        group = null2defaultGroup(group);
        String tenant = agent.getTenant();
        CacheData cache = addCacheDataIfAbsent(dataId, group, tenant);
        cache.setContent(content);
        for (Listener listener : listeners) {
            cache.addListener(listener);
        }
        // if current cache is already at listening status,do not notify.
        if (!cache.isListenSuccess()) {
            agent.notifyListenConfig();
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
        group = null2defaultGroup(group);
        String tenant = agent.getTenant();
        CacheData cache = getCache(dataId, group, tenant);
        if (null != cache) {
            cache.removeListener(listener);
            if (cache.getListeners().isEmpty()) {
                agent.removeCache(dataId, group);
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
     * @return success or not.
     * @throws NacosException exception throw.
     */
    public boolean publishConfig(String dataId, String group, String tenant, String appName, String tag, String betaIps,
            String content) throws NacosException {
        return agent.publishConfig(dataId, group, tenant, appName, tag, betaIps, content);
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
                    String[] ct = getServerConfig(dataId, group, tenant, 3000L, false);
                    cache.setContent(ct[0]);
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
    
    public String[] getServerConfig(String dataId, String group, String tenant, long readTimeout, boolean notify)
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
    
    private String null2defaultGroup(String group) {
        return (null == group) ? Constants.DEFAULT_GROUP : group.trim();
    }
    
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public ClientWorker(final ConfigFilterChainManager configFilterChainManager, final Properties properties)
            throws NacosException {
        this.configFilterChainManager = configFilterChainManager;
        
        init(properties);
    
        ServerListManager serverListManager = new ServerListManager(properties);
        serverListManager.start();
    
        if (ParamUtils.useHttpSwitch()) {
            agent = new ConfigHttpTransportClient(properties, serverListManager);
        } else {
            agent = new ConfigRpcTransportClient(properties, serverListManager);
        }
    
        this.executor = Executors.newScheduledThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.Worker." + agent.getName());
                t.setDaemon(true);
                return t;
            }
        });
        
        this.executorService = Executors
                .newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("com.alibaba.nacos.client.Worker.longPolling." + agent.getName());
                        t.setDaemon(true);
                        return t;
                    }
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
            String[] ct = getServerConfig(cacheData.dataId, cacheData.group, cacheData.tenant, 3000L, notify);
            cacheData.setContent(ct[0]);
            if (null != ct[1]) {
                cacheData.setType(ct[1]);
            }
            cacheData.checkListenerMd5();
        } catch (Exception e) {
            LOGGER.error("refresh content and check md5 fail ,dataid={},group={},tenant={} ", cacheData.dataId,
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
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        LOGGER.info("{} do shutdown begin", className);
        ThreadUtils.shutdownThreadPool(executorService, LOGGER);
        ThreadUtils.shutdownThreadPool(executor, LOGGER);
        LOGGER.info("{} do shutdown stop", className);
    }
    
    public boolean isHealthServer() {
        return isHealthServer;
    }
    
    private void setHealthServer(boolean isHealthServer) {
        this.isHealthServer = isHealthServer;
    }
    
    final ScheduledExecutorService executor;
    
    final ScheduledExecutorService executorService;
    
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
    
    public class ConfigRpcTransportClient extends ConfigTransportClient {
        
        private BlockingQueue<Object> listenExecutebell = new ArrayBlockingQueue<Object>(1);
        
        private Object bellItem = new Object();
    
        private Map<String, RpcClient> rpcClientMap = new HashMap<String, RpcClient>();
        
        public ConfigRpcTransportClient(Properties properties, ServerListManager serverListManager) {
            super(properties, serverListManager);
        }
    
        private ConnectionType getConectiontype() {
            ConnectionType connectionType = ConnectionType.GRPC;
            String connetionType = ParamUtils.configRemoteConnectionType();
            if (StringUtils.isNotBlank(connetionType)) {
                ConnectionType connectionType1 = ConnectionType.valueOf(connetionType);
                if (connectionType1 != null) {
                    connectionType = connectionType1;
                }
            }
            return connectionType;
        }
    
        private Map<String, String> getLabels() {
        
            Map<String, String> labels = new HashMap<String, String>(2, 1);
            labels.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
            labels.put(RemoteConstants.LABEL_MODULE, RemoteConstants.LABEL_MODULE_CONFIG);
            return labels;
        }
    
        private void initHandlerRpcClient(final RpcClient rpcClientInner) {
            /*
             * Register Listen Change Handler
             */
            rpcClientInner.registerServerPushResponseHandler(new ServerRequestHandler() {
                @Override
                public Response requestReply(Request request, RequestMeta requestMeta) {
                    if (request instanceof ConfigChangeNotifyRequest) {
                        ConfigChangeNotifyRequest configChangeNotifyRequest = (ConfigChangeNotifyRequest) request;
                        String groupKey = GroupKey.getKeyTenant(configChangeNotifyRequest.getDataId(),
                                configChangeNotifyRequest.getGroup(), configChangeNotifyRequest.getTenant());
                        CacheData cacheData = cacheMap.get().get(groupKey);
                        if (cacheData != null) {
                            if (configChangeNotifyRequest.isContentPush()
                                    && cacheData.getLastModifiedTs() < configChangeNotifyRequest.getLastModifiedTs()) {
                                cacheData.setContent(configChangeNotifyRequest.getContent());
                                cacheData.setType(configChangeNotifyRequest.getType());
                                cacheData.checkListenerMd5();
                            }
                            cacheData.setListenSuccess(false);
                            notifyListenConfig();
                        }
                        return new ConfigChangeNotifyResponse();
                    }
                    return null;
                }
    
            });
        
            rpcClientInner.registerConnectionListener(new ConnectionEventListener() {
            
                @Override
                public void onConnected() {
                    notifyListenConfig();
                }
            
                @Override
                public void onDisConnect() {
                    String taskId = rpcClientInner.getLabels().get("taskId");
                    LOGGER.info("[0]clear listen context...", rpcClientInner.getName());
                    Collection<CacheData> values = cacheMap.get().values();
                
                    for (CacheData cacheData : values) {
                        if (taskId != null && Integer.valueOf(taskId).equals(cacheData.getTaskId())) {
                            cacheData.setListenSuccess(false);
                            continue;
                        }
                        cacheData.setListenSuccess(false);
                    }
                }
            
            });
        
            rpcClientInner.init(new ServerListFactory() {
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
        }
        
        @Override
        public void startIntenal() throws NacosException {
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            try {
                                listenExecutebell.poll(5L, TimeUnit.SECONDS);
                                executeConfigListen();
                            } catch (Exception e) {
                                LOGGER.error("[ rpc listen execute ] [rpc listen] exception", e);
                            }
                        }
                    } catch (Throwable e) {
                        LOGGER.error("rpc listen task exception", e);
                    }
                }
            }, 0L, TimeUnit.MILLISECONDS);
    
            // register server change subscriber.
            NotifyCenter.registerSubscriber(new Subscriber() {
                @Override
                public void onEvent(Event event) {
                    for (RpcClient rpcClient : rpcClientMap.values()) {
                        RpcClient.ServerInfo currentServer = rpcClient.getCurrentServer();
                        if (currentServer != null) {
                            List<String> serverUrls = serverListManager.getServerUrls();
                            String currentServerIp = currentServer.getServerIp();
                            int currentServerPort = currentServer.getServerPort() - rpcClient.rpcPortOffset();
                            String currentAddress = currentServerIp + ":" + currentServerPort;
                            for (String server : serverUrls) {
                                if (server.equals(currentAddress)) {
                                    rpcClient.switchServerAsync();
                                    return;
                                }
                            }
                        }
                    }
                    
                }
    
                @Override
                public Class<? extends Event> subscribeType() {
                    return ServerlistChangeEvent.class;
                }
            });
            
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
            
            for (CacheData cache : cacheMap.get().values()) {
                //get listen  config and remove listen  config
                if (!CollectionUtils.isEmpty(cache.getListeners()) && !cache.isListenSuccess()) {
                    if (!cache.isUseLocalConfigInfo()) {
                        List<CacheData> cacheDatas = listenCachesMap.get(String.valueOf(cache.getTaskId()));
                        if (cacheDatas == null) {
                            cacheDatas = new LinkedList<CacheData>();
                            listenCachesMap.put(String.valueOf(cache.getTaskId()), cacheDatas);
                        }
                        cacheDatas.add(cache);
                        
                    }
                } else if (CollectionUtils.isEmpty(cache.getListeners()) && cache.isListenSuccess()) {
                    
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
                                for (ConfigChangeBatchListenResponse.ConfigContext changeConfig : configChangeBatchListenResponse
                                        .getChangedConfigs()) {
                                    String changeKey = GroupKey
                                            .getKeyTenant(changeConfig.getDataId(), changeConfig.getGroup(),
                                                    changeConfig.getTenant());
                                    changeKeys.add(changeKey);
                                    refreshContentAndCheck(changeKey, true);
                                }
                            }
    
                            //handler constent configs
                            for (CacheData cacheData : listenCaches) {
                                if (!changeKeys.contains(GroupKey.getKeyTenant(cacheData.dataId, cacheData.group,
                                        cacheData.getTenant()))) {
                                    cacheData.setListenSuccess(true);
                                }
                            }
    
                        }
                    } catch (Exception e) {
                        LOGGER.error("async listen config change error ", e);
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
                                ClientWorker.this.removeCache(cacheData.dataId, cacheData.group, cacheData.tenant);
                            }
                        }
    
                    } catch (Exception e) {
                        LOGGER.error("async remove listen config change error ", e);
                    }
                }
            }
        }
    
        private RpcClient ensureRpcClient(String taskId) throws NacosException {
            Map<String, String> labels = getLabels();
            Map<String, String> newlabels = new HashMap<String, String>(labels);
            newlabels.put("taskId", taskId);
        
            RpcClient rpcClient = RpcClientFactory
                    .createClient("config-" + taskId + "-" + uuid, getConectiontype(), newlabels);
            if (rpcClient.isWaitInited()) {
                initHandlerRpcClient(rpcClient);
                rpcClient.start();
            }
        
            return rpcClient;
        }
    
        /**
         * build config strings.
         *
         * @param caches caches to build config string.
         * @return
         */
        private List<String> buildConfigStrs(List<CacheData> caches) {
            StringBuilder listenConfigsBuilder = new StringBuilder();
            List<String> configStrings = new ArrayList<String>();
            int index = 0;
            for (CacheData cache : caches) {
                index++;
                listenConfigsBuilder.append(cache.dataId).append(WORD_SEPARATOR);
                listenConfigsBuilder.append(cache.group).append(WORD_SEPARATOR);
                if (StringUtils.isBlank(cache.tenant)) {
                    listenConfigsBuilder.append(cache.getMd5()).append(LINE_SEPARATOR);
                } else {
                    listenConfigsBuilder.append(cache.getMd5()).append(WORD_SEPARATOR);
                    listenConfigsBuilder.append(cache.getTenant()).append(LINE_SEPARATOR);
                }
    
                if (index >= 3000) {
                    configStrings.add(listenConfigsBuilder.toString());
                    listenConfigsBuilder = new StringBuilder();
                    index = 0;
                }
            }
    
            if (listenConfigsBuilder.length() > 0) {
                configStrings.add(listenConfigsBuilder.toString());
        
            }
            return configStrings;
        }
    
        /**
         * build config string.
         *
         * @param caches caches to build config string.
         * @return
         */
        private String buildConfigStr(List<CacheData> caches) {
            StringBuilder listenConfigsBuilder = new StringBuilder();
            List<String> configStrings = new ArrayList<String>();
            for (CacheData cache : caches) {
                listenConfigsBuilder.append(cache.dataId).append(WORD_SEPARATOR);
                listenConfigsBuilder.append(cache.group).append(WORD_SEPARATOR);
                if (StringUtils.isBlank(cache.tenant)) {
                    listenConfigsBuilder.append(cache.getMd5()).append(LINE_SEPARATOR);
                } else {
                    listenConfigsBuilder.append(cache.getMd5()).append(WORD_SEPARATOR);
                    listenConfigsBuilder.append(cache.getTenant()).append(LINE_SEPARATOR);
                }
    
            }
    
            return listenConfigsBuilder.toString();
        }
    
        /**
         * build config string.
         *
         * @param caches caches to build config string.
         * @return
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
            // Notify to rpc unlisten ,and remove cache if success.
            notifyListenConfig();
        }
        
        /**
         * send cancel listen config change request .
         *
         * @param configListenString string of remove listen config string.
         */
        private boolean unListenConfigChange(RpcClient rpcClient, ConfigBatchListenRequest configChangeListenRequest)
                throws NacosException {
            
            ConfigChangeBatchListenResponse response = (ConfigChangeBatchListenResponse) requestProxy(rpcClient,
                    configChangeListenRequest);
            return response.isSuccess();
        }
        
        @Override
        public String[] queryConfig(String dataId, String group, String tenant, long readTimeous, boolean notify)
                throws NacosException {
            ConfigQueryRequest request = ConfigQueryRequest.build(dataId, group, tenant);
            request.putHeader("notify", String.valueOf(notify));
            ConfigQueryResponse response = (ConfigQueryResponse) requestProxy(getOneRunningClient(), request);
            
            String[] ct = new String[2];
            if (response.isSuccess()) {
                LocalConfigInfoProcessor.saveSnapshot(this.getName(), dataId, group, tenant, response.getContent());
                ct[0] = response.getContent();
                if (StringUtils.isNotBlank(response.getContentType())) {
                    ct[1] = response.getContentType();
                } else {
                    ct[1] = ConfigType.TEXT.getType();
                }
                return ct;
            } else if (response.getErrorCode() == ConfigQueryResponse.CONFIG_NOT_FOUND) {
                LocalConfigInfoProcessor.saveSnapshot(this.getName(), dataId, group, tenant, null);
                return ct;
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
            try {
                request.putAllHeader(super.getSecurityHeaders());
                request.putAllHeader(super.getSpasHeaders());
            } catch (Exception e) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, e);
            }
        
            JsonObject asJsonObjectTemp = new Gson().toJsonTree(request).getAsJsonObject();
            asJsonObjectTemp.remove("headers");
            asJsonObjectTemp.remove("requestId");
            boolean limit = Limiter.isLimit(request.getClass() + asJsonObjectTemp.toString());
            if (limit) {
                throw new NacosException(NacosException.CLIENT_OVER_THRESHOLD,
                        "More than client-side current limit threshold");
            }
            return rpcClientInner.request(request);
        }
    
        RpcClient getOneRunningClient() throws NacosException {
            return ensureRpcClient("0");
        }
        
        @Override
        public boolean publishConfig(String dataId, String group, String tenant, String appName, String tag,
                String betaIps, String content) throws NacosException {
            try {
                ConfigPublishRequest request = new ConfigPublishRequest(dataId, group, tenant, content);
                request.putAdditonalParam("tag", tag);
                request.putAdditonalParam("appName", appName);
                request.putAdditonalParam("betaIps", betaIps);
                ConfigPubishResponse response = (ConfigPubishResponse) requestProxy(getOneRunningClient(), request);
                return response.isSuccess();
            } catch (Exception e) {
                LOGGER.warn("[{}] [publish-single] error, dataId={}, group={}, tenant={}, code={}, msg={}",
                        this.getName(), dataId, group, tenant, "unkonw", e.getMessage());
                return false;
            }
        }
        
        @Override
        public boolean removeConfig(String dataid, String group, String tenat, String tag) throws NacosException {
            ConfigRemoveRequest request = new ConfigRemoveRequest(dataid, group, tenat, tag);
            ConfigRemoveResponse response = (ConfigRemoveResponse) requestProxy(getOneRunningClient(), request);
            return response.isSuccess();
        }
    }
    
    public class ConfigHttpTransportClient extends ConfigTransportClient {
        
        private static final long POST_TIMEOUT = 3000L;
        
        HttpAgent agent;
        
        private double currentLongingTaskCount = 0;
        
        public ConfigHttpTransportClient(Properties properties, ServerListManager serverListManager)
                throws NacosException {
            
            super(properties, serverListManager);
            agent = new MetricsHttpAgent(new ServerHttpAgent(properties));
            
        }
        
        @Override
        public void startIntenal() {
            
            executor.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        executeConfigListen();
                    } catch (Throwable e) {
                        LOGGER.error("[" + agent.getName() + "] [sub-check] rotate check error", e);
                    }
                }
            }, 1L, 10L, TimeUnit.MILLISECONDS);
        }
        
        @Override
        public String getName() {
            return agent.getName();
        }
        
        @Override
        public void notifyListenConfig() {
            //Do nothing.
        }
        
        @Override
        public void executeConfigListen() {
            // Dispatch taskes.
            int listenerSize = cacheMap.get().size();
            // Round up the longingTaskCount.
            int longingTaskCount = (int) Math.ceil(listenerSize / ParamUtil.getPerTaskConfigSize());
            if (longingTaskCount > currentLongingTaskCount) {
                for (int i = (int) currentLongingTaskCount; i < longingTaskCount; i++) {
                    // The task list is no order.So it maybe has issues when changing.
                    executorService.execute(new LongPollingRunnable(agent, i, this));
                }
                currentLongingTaskCount = longingTaskCount;
            }
        }
        
        @Override
        public void removeCache(String dataId, String group) {
            //remove cache directory in http model
            ClientWorker.this.removeCache(dataId, group);
        }
        
        @Override
        public String[] queryConfig(String dataId, String group, String tenant, long readTimeout, boolean notify)
                throws NacosException {
            String[] ct = new String[2];
            if (StringUtils.isBlank(group)) {
                group = Constants.DEFAULT_GROUP;
            }
            
            HttpRestResult<String> result = null;
            try {
                Map<String, String> params = new HashMap<String, String>(3);
                if (StringUtils.isBlank(tenant)) {
                    params.put("dataId", dataId);
                    params.put("group", group);
                } else {
                    params.put("dataId", dataId);
                    params.put("group", group);
                    params.put("tenant", tenant);
                }
    
                Map<String, String> headers = new HashMap<String, String>(16);
                headers.put("notify", String.valueOf(notify));
                result = httpGet(Constants.CONFIG_CONTROLLER_PATH, headers, params, agent.getEncode(), readTimeout);
            } catch (Exception ex) {
                String message = String
                        .format("[%s] [sub-server] get server config exception, dataId=%s, group=%s, tenant=%s",
                                agent.getName(), dataId, group, tenant);
                LOGGER.error(message, ex);
                throw new NacosException(NacosException.SERVER_ERROR, ex);
            }
            
            switch (result.getCode()) {
                case HttpURLConnection.HTTP_OK:
                    LocalConfigInfoProcessor.saveSnapshot(agent.getName(), dataId, group, tenant, result.getData());
                    ct[0] = result.getData();
                    if (result.getHeader().getValue(CONFIG_TYPE) != null) {
                        ct[1] = result.getHeader().getValue(CONFIG_TYPE);
                    } else {
                        ct[1] = ConfigType.TEXT.getType();
                    }
                    return ct;
                case HttpURLConnection.HTTP_NOT_FOUND:
                    LocalConfigInfoProcessor.saveSnapshot(agent.getName(), dataId, group, tenant, null);
                    return ct;
                case HttpURLConnection.HTTP_CONFLICT: {
                    LOGGER.error(
                            "[{}] [sub-server-error] get server config being modified concurrently, dataId={}, group={}, "
                                    + "tenant={}", agent.getName(), dataId, group, tenant);
                    throw new NacosException(NacosException.CONFLICT,
                            "data being modified, dataId=" + dataId + ",group=" + group + ",tenant=" + tenant);
                }
                case HttpURLConnection.HTTP_FORBIDDEN: {
                    LOGGER.error("[{}] [sub-server-error] no right, dataId={}, group={}, tenant={}", agent.getName(),
                            dataId, group, tenant);
                    throw new NacosException(result.getCode(), result.getMessage());
                }
                default: {
                    LOGGER.error("[{}] [sub-server-error]  dataId={}, group={}, tenant={}, code={}", agent.getName(),
                            dataId, group, tenant, result.getCode());
                    throw new NacosException(result.getCode(),
                            "http error, code=" + result.getCode() + ",dataId=" + dataId + ",group=" + group
                                    + ",tenant=" + tenant);
                }
            }
        }
    
        private void assembleHttpParams(Map<String, String> params, Map<String, String> headers) throws Exception {
            Map<String, String> securityHeaders = super.getSecurityHeaders();
            if (securityHeaders != null) {
                //put security header to param
                params.putAll(securityHeaders);
                if (StringUtils.isNotBlank(super.tenant) && !params.containsKey(SpasAdapter.TENANT_KEY)) {
                    params.put(SpasAdapter.TENANT_KEY, tenant);
                }
            }
            Map<String, String> spasHeaders = super.getSpasHeaders();
            if (spasHeaders != null) {
                //put spasHeader to header.
                headers.putAll(spasHeaders);
            }
            Map<String, String> commonHeader = super.getCommonHeader();
            if (commonHeader != null) {
                //put common headers
                headers.putAll(commonHeader);
            }
            Map<String, String> signHeaders = SpasAdapter.getSignHeaders(params, super.secretKey);
            if (signHeaders != null) {
                headers.putAll(signHeaders);
            }
        
        }
        
        @Override
        public boolean publishConfig(String dataId, String group, String tenant, String appName, String tag,
                String betaIps, String content) throws NacosException {
            group = null2defaultGroup(group);
            ParamUtils.checkParam(dataId, group, content);
            
            ConfigRequest cr = new ConfigRequest();
            cr.setDataId(dataId);
            cr.setTenant(tenant);
            cr.setGroup(group);
            cr.setContent(content);
            configFilterChainManager.doFilter(cr, null);
            content = cr.getContent();
            
            String url = Constants.CONFIG_CONTROLLER_PATH;
            Map<String, String> params = new HashMap<String, String>(6);
            params.put("dataId", dataId);
            params.put("group", group);
            params.put("content", content);
            if (StringUtils.isNotEmpty(tenant)) {
                params.put("tenant", tenant);
            }
            if (StringUtils.isNotEmpty(appName)) {
                params.put("appName", appName);
            }
            if (StringUtils.isNotEmpty(tag)) {
                params.put("tag", tag);
            }
            Map<String, String> headers = new HashMap<String, String>(1);
            if (StringUtils.isNotEmpty(betaIps)) {
                headers.put("betaIps", betaIps);
            }
            
            HttpRestResult<String> result = null;
            try {
    
                result = httpPost(url, headers, params, encode, POST_TIMEOUT);
            } catch (Exception ex) {
                LOGGER.warn("[{}] [publish-single] exception, dataId={}, group={}, msg={}", agent.getName(), dataId,
                        group, ex.toString());
                return false;
            }
            
            if (result.ok()) {
                LOGGER.info("[{}] [publish-single] ok, dataId={}, group={}, tenant={}, config={}", agent.getName(),
                        dataId, group, tenant, ContentUtils.truncateContent(content));
                return true;
            } else if (HttpURLConnection.HTTP_FORBIDDEN == result.getCode()) {
                LOGGER.warn("[{}] [publish-single] error, dataId={}, group={}, tenant={}, code={}, msg={}",
                        agent.getName(), dataId, group, tenant, result.getCode(), result.getMessage());
                throw new NacosException(result.getCode(), result.getMessage());
            } else {
                LOGGER.warn("[{}] [publish-single] error, dataId={}, group={}, tenant={}, code={}, msg={}",
                        agent.getName(), dataId, group, tenant, result.getCode(), result.getMessage());
                return false;
            }
        }
    
        private HttpRestResult<String> httpPost(String path, Map<String, String> headers,
                Map<String, String> paramValues, String encoding, long readTimeoutMs) throws Exception {
            if (headers == null) {
                headers = new HashMap<String, String>(16);
            }
            assembleHttpParams(paramValues, headers);
            return agent.httpPost(path, headers, paramValues, encoding, readTimeoutMs);
        }
    
        private HttpRestResult<String> httpGet(String path, Map<String, String> headers,
                Map<String, String> paramValues, String encoding, long readTimeoutMs) throws Exception {
            if (headers == null) {
                headers = new HashMap<String, String>(16);
            }
            assembleHttpParams(paramValues, headers);
            return agent.httpGet(path, headers, paramValues, encoding, readTimeoutMs);
        }
    
        private HttpRestResult<String> httpDelete(String path, Map<String, String> headers,
                Map<String, String> paramValues, String encoding, long readTimeoutMs) throws Exception {
            if (headers == null) {
                headers = new HashMap<String, String>(16);
            }
            assembleHttpParams(paramValues, headers);
            return agent.httpDelete(path, headers, paramValues, encoding, readTimeoutMs);
        }
        
        @Override
        public boolean removeConfig(String dataId, String group, String tenat, String tag) throws NacosException {
            
            group = null2defaultGroup(group);
            ParamUtils.checkKeyParam(dataId, group);
            String url = Constants.CONFIG_CONTROLLER_PATH;
            Map<String, String> params = new HashMap<String, String>(4);
            params.put("dataId", dataId);
            params.put("group", group);
            
            if (StringUtils.isNotEmpty(tenant)) {
                params.put("tenant", tenant);
            }
            if (StringUtils.isNotEmpty(tag)) {
                params.put("tag", tag);
            }
            
            HttpRestResult<String> result = null;
            try {
                result = httpDelete(url, null, params, encode, POST_TIMEOUT);
            } catch (Exception ex) {
                LOGGER.warn("[remove] error, " + dataId + ", " + group + ", " + tenant + ", msg: " + ex.toString());
                return false;
            }
            
            if (result.ok()) {
                LOGGER.info("[{}] [remove] ok, dataId={}, group={}, tenant={}", agent.getName(), dataId, group, tenant);
                return true;
            } else if (HttpURLConnection.HTTP_FORBIDDEN == result.getCode()) {
                LOGGER.warn("[{}] [remove] error, dataId={}, group={}, tenant={}, code={}, msg={}", agent.getName(),
                        dataId, group, tenant, result.getCode(), result.getMessage());
                throw new NacosException(result.getCode(), result.getMessage());
            } else {
                LOGGER.warn("[{}] [remove] error, dataId={}, group={}, tenant={}, code={}, msg={}", agent.getName(),
                        dataId, group, tenant, result.getCode(), result.getMessage());
                return false;
            }
        }
    
    }
    
    /**
     * config change task of http long polling .
     */
    class LongPollingRunnable implements Runnable {
        
        private final int taskId;
        
        private HttpAgent httpAgent;
    
        private ConfigTransportClient configTransportClient;
    
        public LongPollingRunnable(HttpAgent httpAgent, int taskId, ConfigTransportClient configTransportClient) {
            this.taskId = taskId;
            this.httpAgent = httpAgent;
            this.configTransportClient = configTransportClient;
        }
        
        @Override
        public void run() {
            
            List<CacheData> cacheDatas = new ArrayList<CacheData>();
            List<String> inInitializingCacheList = new ArrayList<String>();
            try {
                // check failover config
                for (CacheData cacheData : cacheMap.get().values()) {
                    if (cacheData.getTaskId() == taskId) {
                        cacheDatas.add(cacheData);
                        try {
                            checkLocalConfig(httpAgent.getName(), cacheData);
                            if (cacheData.isUseLocalConfigInfo()) {
                                cacheData.checkListenerMd5();
                            }
                        } catch (Exception e) {
                            LOGGER.error("get local config info error", e);
                        }
                    }
                }
                
                // check server config
                List<String> changedGroupKeys = checkUpdateDataIds(httpAgent, configTransportClient, cacheDatas,
                        inInitializingCacheList);
                if (!CollectionUtils.isEmpty(changedGroupKeys)) {
                    LOGGER.info("get changedGroupKeys:" + changedGroupKeys);
                }
                
                for (String groupKey : changedGroupKeys) {
                    String[] key = GroupKey.parseKey(groupKey);
                    String dataId = key[0];
                    String group = key[1];
                    String tenant = null;
                    if (key.length == 3) {
                        tenant = key[2];
                    }
                    try {
                        String[] ct = getServerConfig(dataId, group, tenant, 3000L, true);
                        CacheData cache = cacheMap.get().get(GroupKey.getKeyTenant(dataId, group, tenant));
                        cache.setContent(ct[0]);
                        if (null != ct[1]) {
                            cache.setType(ct[1]);
                        }
                        LOGGER.info("[{}] [data-received] dataId={}, group={}, tenant={}, md5={}, content={}, type={}",
                                httpAgent.getName(), dataId, group, tenant, cache.getMd5(),
                                ContentUtils.truncateContent(ct[0]), ct[1]);
                    } catch (NacosException ioe) {
                        String message = String
                                .format("[%s] [get-update] get changed config exception. dataId=%s, group=%s, tenant=%s",
                                        httpAgent.getName(), dataId, group, tenant);
                        LOGGER.error(message, ioe);
                    }
                }
                for (CacheData cacheData : cacheDatas) {
                    if (!cacheData.isInitializing() || inInitializingCacheList
                            .contains(GroupKey.getKeyTenant(cacheData.dataId, cacheData.group, cacheData.tenant))) {
                        cacheData.checkListenerMd5();
                        cacheData.setInitializing(false);
                    }
                }
                inInitializingCacheList.clear();
                
                executorService.execute(this);
                
            } catch (Throwable e) {
                
                // If the rotation training task is abnormal, the next execution time of the task will be punished
                LOGGER.error("longPolling error : ", e);
                executorService.schedule(this, taskPenaltyTime, TimeUnit.MILLISECONDS);
            }
        }
    }
    
    
    /**
     * Fetch the dataId list from server.
     *
     * @param cacheDatas              CacheDatas for config infomations.
     * @param inInitializingCacheList initial cache lists.
     * @return String include dataId and group (ps: it maybe null).
     * @throws Exception Exception.
     */
    List<String> checkUpdateDataIds(HttpAgent httpAgent, ConfigTransportClient configTransportClient,
            List<CacheData> cacheDatas, List<String> inInitializingCacheList) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (CacheData cacheData : cacheDatas) {
            if (!cacheData.isUseLocalConfigInfo()) {
                sb.append(cacheData.dataId).append(WORD_SEPARATOR);
                sb.append(cacheData.group).append(WORD_SEPARATOR);
                if (StringUtils.isBlank(cacheData.tenant)) {
                    sb.append(cacheData.getMd5()).append(LINE_SEPARATOR);
                } else {
                    sb.append(cacheData.getMd5()).append(WORD_SEPARATOR);
                    sb.append(cacheData.getTenant()).append(LINE_SEPARATOR);
                }
                if (cacheData.isInitializing()) {
                    // It updates when cacheData occours in cacheMap by first time.
                    inInitializingCacheList
                            .add(GroupKey.getKeyTenant(cacheData.dataId, cacheData.group, cacheData.tenant));
                }
            }
        }
        boolean isInitializingCacheList = !inInitializingCacheList.isEmpty();
        return checkUpdateConfigStr(httpAgent, configTransportClient, sb.toString(), isInitializingCacheList);
    }
    
    /**
     * Fetch the updated dataId list from server.
     *
     * @param probeUpdateString       updated attribute string value.
     * @param isInitializingCacheList initial cache lists.
     * @return The updated dataId list(ps: it maybe null).
     * @throws IOException Exception.
     */
    List<String> checkUpdateConfigStr(HttpAgent httpAgent, ConfigTransportClient configTransportClient,
            String probeUpdateString, boolean isInitializingCacheList) throws Exception {
        
        Map<String, String> params = new HashMap<String, String>(2);
        params.put(Constants.PROBE_MODIFY_REQUEST, probeUpdateString);
        Map<String, String> headers = new HashMap<String, String>(2);
        headers.put("Long-Pulling-Timeout", "" + timeout);
    
        // told server do not hang me up if new initializing cacheData added in
        if (isInitializingCacheList) {
            headers.put("Long-Pulling-Timeout-No-Hangup", "true");
        }
    
        if (StringUtils.isBlank(probeUpdateString)) {
            return Collections.emptyList();
        }
    
        try {
        
            //assemble headers.
            Map<String, String> securityHeaders = configTransportClient.getSecurityHeaders();
            if (securityHeaders != null) {
                //put security header to param
                params.putAll(securityHeaders);
            }
            Map<String, String> spasHeaders = configTransportClient.getSpasHeaders();
            if (spasHeaders != null) {
                //put spasHeader to header.
                headers.putAll(spasHeaders);
            }
            Map<String, String> commonHeader = configTransportClient.getCommonHeader();
            if (commonHeader != null) {
                //put common headers
                headers.putAll(commonHeader);
            }
            Map<String, String> signHeaders = SpasAdapter.getSignHeaders(params, configTransportClient.secretKey);
            if (signHeaders != null) {
                headers.putAll(signHeaders);
            }
            
            // In order to prevent the server from handling the delay of the client's long task,
            // increase the client's read timeout to avoid this problem.
        
            long readTimeoutMs = timeout + (long) Math.round(timeout >> 1);
            HttpRestResult<String> result = httpAgent
                    .httpPost(Constants.CONFIG_CONTROLLER_PATH + "/listener", headers, params, httpAgent.getEncode(),
                            readTimeoutMs);
        
            if (result.ok()) {
                setHealthServer(true);
                return parseUpdateDataIdResponse(httpAgent, result.getData());
            } else {
                setHealthServer(false);
                LOGGER.error("[{}] [check-update] get changed dataId error, code: {}", httpAgent.getName(),
                        result.getCode());
            }
        } catch (Exception e) {
            setHealthServer(false);
            LOGGER.error("[" + httpAgent.getName() + "] [check-update] get changed dataId exception", e);
            throw e;
        }
        return Collections.emptyList();
    }
    
    /**
     * Get the groupKey list from the http response.
     *
     * @param response Http response.
     * @return GroupKey List, (ps: it maybe null).
     */
    private List<String> parseUpdateDataIdResponse(HttpAgent httpAgent, String response) {
        if (StringUtils.isBlank(response)) {
            return Collections.emptyList();
        }
    
        try {
            response = URLDecoder.decode(response, "UTF-8");
        } catch (Exception e) {
            LOGGER.error("[" + httpAgent.getName() + "] [polling-resp] decode modifiedDataIdsString error", e);
        }
    
        List<String> updateList = new LinkedList<String>();
    
        for (String dataIdAndGroup : response.split(LINE_SEPARATOR)) {
            if (!StringUtils.isBlank(dataIdAndGroup)) {
                String[] keyArr = dataIdAndGroup.split(WORD_SEPARATOR);
                String dataId = keyArr[0];
                String group = keyArr[1];
                if (keyArr.length == 2) {
                    updateList.add(GroupKey.getKey(dataId, group));
                    LOGGER.info("[{}] [polling-resp] config changed. dataId={}, group={}", httpAgent.getName(), dataId,
                            group);
                } else if (keyArr.length == 3) {
                    String tenant = keyArr[2];
                    updateList.add(GroupKey.getKeyTenant(dataId, group, tenant));
                    LOGGER.info("[{}] [polling-resp] config changed. dataId={}, group={}, tenant={}",
                            httpAgent.getName(), dataId, group, tenant);
                } else {
                    LOGGER.error("[{}] [polling-resp] invalid dataIdAndGroup error {}", httpAgent.getName(),
                            dataIdAndGroup);
                }
            }
        }
        return updateList;
    }
    
    /**
     * get client worker agent.
     *
     * @return
     */
    public String getAgentName() {
        return this.agent.getName();
    }
    
}
