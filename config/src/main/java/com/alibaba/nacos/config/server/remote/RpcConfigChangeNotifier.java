/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.remote.AbstractPushCallBack;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.remote.control.TpsMonitorManager;
import com.alibaba.nacos.core.remote.control.TpsMonitorPoint;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ConfigChangeNotifier.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifier.java, v 0.1 2020年07月20日 3:00 PM liuzunfei Exp $
 */
@Component(value = "rpcConfigChangeNotifier")
public class RpcConfigChangeNotifier extends Subscriber<LocalDataChangeEvent> {
    
    private static final String POINT_CONFIG_PUSH = "CONFIG_PUSH_COUNT";
    
    private static final String POINT_CONFIG_PUSH_SUCCESS = "CONFIG_PUSH_SUCCESS";
    
    private static final String POINT_CONFIG_PUSH_FAIL = "CONFIG_PUSH_FAIL";
    
    @Autowired
    private TpsMonitorManager tpsMonitorManager;
    
    public RpcConfigChangeNotifier() {
        NotifyCenter.registerSubscriber(this);
    }
    
    @PostConstruct
    private void registerTpsPoint() {
        
        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(POINT_CONFIG_PUSH));
        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(POINT_CONFIG_PUSH_SUCCESS));
        tpsMonitorManager.registerTpsControlPoint(new TpsMonitorPoint(POINT_CONFIG_PUSH_FAIL));
        
    }
    
    @Autowired
    ConfigChangeListenContext configChangeListenContext;
    
    @Autowired
    private RpcPushService rpcPushService;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    /**
     * adaptor to config module ,when server side config change ,invoke this method.
     *
     * @param groupKey groupKey
     */
    public void configDataChanged(String groupKey, String dataId, String group, String tenant, boolean isBeta,
            List<String> betaIps, String tag) {
        
        Set<String> listeners = configChangeListenContext.getListeners(groupKey);
        if (CollectionUtils.isEmpty(listeners)) {
            return;
        }
        int notifyClientCount = 0;
        for (final String client : listeners) {
            Connection connection = connectionManager.getConnection(client);
            if (connection == null) {
                continue;
            }

            //beta ips check.
            String clientIp = connection.getMetaInfo().getClientIp();
            String clientTag = connection.getMetaInfo().getTag();
            if (isBeta && betaIps != null && !betaIps.contains(clientIp)) {
                continue;
            }
            //tag check
            if (StringUtils.isNotBlank(tag) && !tag.equals(clientTag)) {
                continue;
            }

            ConfigChangeNotifyRequest notifyRequest = ConfigChangeNotifyRequest.build(dataId, group, tenant);

            RpcPushTask rpcPushRetryTask = new RpcPushTask(notifyRequest, 50, client, clientIp,
                    connection.getMetaInfo().getAppName());
            push(rpcPushRetryTask);
            notifyClientCount++;
        }
        Loggers.REMOTE_PUSH.info("push [{}] clients ,groupKey=[{}]", notifyClientCount, groupKey);
    }
    
    @Override
    public void onEvent(LocalDataChangeEvent event) {
        String groupKey = event.groupKey;
        boolean isBeta = event.isBeta;
        List<String> betaIps = event.betaIps;
        String[] strings = GroupKey.parseKey(groupKey);
        String dataId = strings[0];
        String group = strings[1];
        String tenant = strings.length > 2 ? strings[2] : "";
        String tag = event.tag;
        
        configDataChanged(groupKey, dataId, group, tenant, isBeta, betaIps, tag);
        
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return LocalDataChangeEvent.class;
    }
    
    class RpcPushTask implements Runnable {
        
        ConfigChangeNotifyRequest notifyRequest;
        
        int maxRetryTimes = -1;
        
        int tryTimes = 0;
        
        String connectionId;
        
        String clientIp;
        
        String appName;
        
        public RpcPushTask(ConfigChangeNotifyRequest notifyRequest, int maxRetryTimes, String connectionId,
                String clientIp, String appName) {
            this.notifyRequest = notifyRequest;
            this.maxRetryTimes = maxRetryTimes;
            this.connectionId = connectionId;
            this.clientIp = clientIp;
            this.appName = appName;
        }
        
        public boolean isOverTimes() {
            return maxRetryTimes > 0 && this.tryTimes >= maxRetryTimes;
        }
        
        @Override
        public void run() {
            tryTimes++;
            if (!tpsMonitorManager.applyTpsForClientIp(POINT_CONFIG_PUSH, connectionId, clientIp)) {
                push(this);
            } else {
                rpcPushService.pushWithCallback(connectionId, notifyRequest, new AbstractPushCallBack(3000L) {
                    @Override
                    public void onSuccess() {
                        tpsMonitorManager.applyTpsForClientIp(POINT_CONFIG_PUSH_SUCCESS, connectionId, clientIp);
                    }
                    
                    @Override
                    public void onFail(Throwable e) {
                        tpsMonitorManager.applyTpsForClientIp(POINT_CONFIG_PUSH_FAIL, connectionId, clientIp);
                        Loggers.REMOTE_PUSH.warn("Push fail", e);
                        push(RpcPushTask.this);
                    }
                    
                }, ConfigExecutor.getClientConfigNotifierServiceExecutor());
                
            }
            
        }
    }
    
    private void push(RpcPushTask retryTask) {
        ConfigChangeNotifyRequest notifyRequest = retryTask.notifyRequest;
        if (retryTask.isOverTimes()) {
            Loggers.REMOTE_PUSH
                    .warn("push callback retry fail over times .dataId={},group={},tenant={},clientId={},will unregister client.",
                            notifyRequest.getDataId(), notifyRequest.getGroup(), notifyRequest.getTenant(),
                            retryTask.connectionId);
            connectionManager.unregister(retryTask.connectionId);
        } else if (connectionManager.getConnection(retryTask.connectionId) != null) {
            // first time :delay 0s; sencond time:delay 2s  ;third time :delay 4s
            ConfigExecutor.getClientConfigNotifierServiceExecutor()
                    .schedule(retryTask, retryTask.tryTimes * 2, TimeUnit.SECONDS);
        } else {
            // client is already offline,ingnore task.
        }
        
    }
}

