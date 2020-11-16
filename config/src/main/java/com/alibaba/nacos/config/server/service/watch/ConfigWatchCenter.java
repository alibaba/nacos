/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.config.server.service.watch;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.config.server.utils.LogUtil.MEMORY_LOG;

/**
 * Configure monitoring center. responsible for all client configuration monitoring operations.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Service
@Scope(proxyMode = ScopedProxyMode.NO)
public class ConfigWatchCenter extends Subscriber<LocalDataChangeEvent> {
    
    private static final int SAMPLE_PERIOD = 100;
    
    private static final int SAMPLE_TIMES = 3;
    
    private final WatchClientManager clientManager;
    
    public ConfigWatchCenter() {
        // Register LocalDataChangeEvent to NotifyCenter.
        NotifyCenter.registerToPublisher(LocalDataChangeEvent.class, NotifyCenter.ringBufferSize);
        NotifyCenter.registerSubscriber(this);
        this.clientManager = new WatchClientManager();
        ConfigExecutor.scheduleLongPolling(new StatTask(), 0L, 10L, TimeUnit.SECONDS);
    }
    
    public void addWatchClient(final WatchClient client) {
        clientManager.addWatchClient(client);
    }
    
    @Override
    public void onEvent(LocalDataChangeEvent event) {
        final String[] metaKey = GroupKey.parseKey(event.groupKey);
        final String namespace = ParamUtils.processNamespace(metaKey[2]);
        final String groupID = ParamUtils.processGroupID(metaKey[1]);
        final String dataID = ParamUtils.processDataID(metaKey[0]);
        final boolean isBeta = event.isBeta;
        final String groupKey = event.groupKey;
        final Set<WatchClient> clients = clientManager.findClientsByGroupKey(namespace, groupID, dataID);
        final long changeTime = System.currentTimeMillis();
        event.setChangeTime(changeTime);
        clients.parallelStream().forEach(client -> {
            ConfigCacheService.getContentBetaMd5(groupKey);
            for (WatchClient clientSub : clientManager.findClientsByGroupKey(namespace, groupID, dataID)) {
                // If published tag is not in the beta list, then it skipped.
                if (isBeta && !CollectionUtils.contains(event.betaIps, clientSub.getIdentity())) {
                    continue;
                }
                
                // If published tag is not in the tag list, then it skipped.
                if (StringUtils.isNotBlank(event.tag) && !event.tag.equals(clientSub.getTag())) {
                    continue;
                }
                
                clientManager.getRetainIps().put(clientSub.getIdentity(), changeTime);
                clientSub.notifyChangeEvent(event);
            }
        });
    }
    
    public SampleResult getSubscribeInfoByIp(String address) {
        SampleResult sampleResult = new SampleResult();
        Map<String, String> listenersGroupKeyStatus = new HashMap<>(64);
        
        for (WatchClient watchClient : clientManager.findClientByAddress(address)) {
            // One ip can have multiple listener.
            listenersGroupKeyStatus.putAll(watchClient.getWatchKey());
        }
        sampleResult.setLisentersGroupkeyStatus(listenersGroupKeyStatus);
        return sampleResult;
    }
    
    public SampleResult getSubscribeInfo(String dataId, String group, String namespace) {
        String groupKey = GroupKey.getKeyTenant(dataId, group, namespace);
        SampleResult sampleResult = new SampleResult();
        Map<String, String> listenersGroupKeyStatus = new HashMap<>(64);
        
        for (WatchClient watchClient : clientManager.findClientsByGroupKey(namespace, group, dataId)) {
            listenersGroupKeyStatus.put(watchClient.getIdentity(), watchClient.getWatchKey().get(groupKey));
        }
        sampleResult.setLisentersGroupkeyStatus(listenersGroupKeyStatus);
        return sampleResult;
    }
    
    /**
     * Aggregate the sampling IP and monitoring configuration information in the sampling results. There is no problem
     * for the merging strategy to cover the previous one with the latter.
     *
     * @param sampleResults sample Results.
     * @return Results.
     */
    public SampleResult mergeSampleResult(List<SampleResult> sampleResults) {
        SampleResult mergeResult = new SampleResult();
        Map<String, String> listenersGroupKeyStatus = new HashMap<>(64);
        for (SampleResult sampleResult : sampleResults) {
            Map<String, String> listenersGroupKeyStatusTmp = sampleResult.getLisentersGroupkeyStatus();
            listenersGroupKeyStatusTmp.replaceAll((k, v) -> v);
        }
        mergeResult.setLisentersGroupkeyStatus(listenersGroupKeyStatus);
        return mergeResult;
    }
    
    public SampleResult getCollectSubscribeInfo(String dataId, String group, String tenant) {
        List<SampleResult> sampleResultLst = new ArrayList<>(50);
        for (int i = 0; i < SAMPLE_TIMES; i++) {
            SampleResult sampleTmp = getSubscribeInfo(dataId, group, tenant);
            if (sampleTmp != null) {
                sampleResultLst.add(sampleTmp);
            }
            if (i < SAMPLE_TIMES - 1) {
                try {
                    Thread.sleep(SAMPLE_PERIOD);
                } catch (InterruptedException e) {
                    LogUtil.CLIENT_LOG.error("sleep wrong", e);
                }
            }
        }
        
        return mergeSampleResult(sampleResultLst);
    }
    
    public SampleResult getCollectSubscribeInfoByIp(String ip) {
        SampleResult sampleResult = new SampleResult();
        sampleResult.setLisentersGroupkeyStatus(new HashMap<>(64));
        for (int i = 0; i < SAMPLE_TIMES; i++) {
            SampleResult sampleTmp = getSubscribeInfoByIp(ip);
            if (sampleTmp != null) {
                if (sampleTmp.getLisentersGroupkeyStatus() != null && !sampleResult.getLisentersGroupkeyStatus()
                        .equals(sampleTmp.getLisentersGroupkeyStatus())) {
                    sampleResult.getLisentersGroupkeyStatus().putAll(sampleTmp.getLisentersGroupkeyStatus());
                }
            }
            if (i < SAMPLE_TIMES - 1) {
                try {
                    Thread.sleep(SAMPLE_PERIOD);
                } catch (InterruptedException e) {
                    LogUtil.CLIENT_LOG.error("sleep wrong", e);
                }
            }
        }
        return sampleResult;
    }
    
    /**
     * Collect application subscribe configinfos.
     *
     * @return configinfos results.
     */
    public Map<String, Set<String>> collectApplicationSubscribeConfigInfos() {
        HashMap<String, Set<String>> app2GroupKeys = new HashMap<>(64);
        clientManager.forEach(client -> {
            if (StringUtils.isEmpty(client.getAppName()) || "unknown".equalsIgnoreCase(client.getAppName())) {
                return;
            }
            Set<String> appSubscribeConfigs = app2GroupKeys.get(client.getAppName());
            Set<String> clientSubscribeConfigs = client.getWatchKey().keySet();
            if (appSubscribeConfigs == null) {
                appSubscribeConfigs = new HashSet<>(clientSubscribeConfigs.size());
            }
            appSubscribeConfigs.addAll(clientSubscribeConfigs);
            app2GroupKeys.put(client.getAppName(), appSubscribeConfigs);
        });
        return app2GroupKeys;
    }
    
    class StatTask implements Runnable {
        
        @Override
        public void run() {
            MEMORY_LOG.info("[config-watch] client count " + clientManager.currentWatchClientCount());
            MetricsMonitor.getLongPollingMonitor().set(clientManager.currentWatchClientCount());
        }
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return LocalDataChangeEvent.class;
    }
}
