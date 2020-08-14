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
import com.alibaba.nacos.api.remote.response.AbstractPushCallBack;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ConfigChangeNotifier.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifier.java, v 0.1 2020年07月20日 3:00 PM liuzunfei Exp $
 */
@Component
public class ConfigChangeNotifier {
    
    private ThreadPoolExecutor retryPushexecutors = new ThreadPoolExecutor(15, 30, 5, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(100000), new ThreadPoolExecutor.AbortPolicy());
    
    @Autowired
    ConfigChangeListenContext configChangeListenContext;
    
    @Autowired
    private RpcPushService rpcPushService;
    
    /**
     * adaptor to config module ,when server side congif change ,invoke this method.
     *
     * @param groupKey       groupKey
     * @param notifyRequet notifyRequet
     */
    public void configDataChanged(String groupKey, final ConfigChangeNotifyRequest notifyRequet) {
        
        Set<String> clients = configChangeListenContext.getListeners(groupKey);
    
        if (!CollectionUtils.isEmpty(clients)) {
            for (final String client : clients) {
                rpcPushService.pushWithCallback(client, notifyRequet, new AbstractPushCallBack(500L) {
                 
                    @Override
                    public void onSuccess() {
                        Loggers.CORE.info("push callback success.,groupKey={},clientId={}", groupKey, client);
                    }
    
                    @Override
                    public void onFail(Exception e) {
                        Loggers.CORE
                                .warn("push callback fail.will retry push ,groupKey={},clientId={}", groupKey, client);
                        retryPush(client, notifyRequet, 3);
                    }
    
                    @Override
                    public void onTimeout() {
                        Loggers.CORE.warn("push callback timeout.will retry push ,groupKey={},clientId={}", groupKey,
                                client);
                        retryPush(client, notifyRequet, 3);
                    }
                });
    
            }
        }
    }
    
    void retryPush(final String clientId, final ConfigChangeNotifyRequest notifyRequet, final int maxRetyTimes) {
        
        try {
            retryPushexecutors.submit(new Runnable() {
                @Override
                public void run() {
                    int maxTimes = maxRetyTimes;
                    boolean rePushFlag = false;
                    while (maxTimes > 0 && !rePushFlag) {
                        maxTimes--;
                        boolean push = rpcPushService.push(clientId, notifyRequet, 1000L);
                        if (push) {
                            rePushFlag = true;
                        }
                    }
                    if (rePushFlag) {
                        Loggers.CORE.warn("push callback retry success.dataId={},group={},tenant={},clientId={}",
                                notifyRequet.getDataId(), notifyRequet.getGroup(), notifyRequet.getTenant(),
                                clientId);
                    } else {
                        Loggers.CORE.error(String
                                .format("push callback retry fail.dataId={},group={},tenant={},clientId={}",
                                        notifyRequet.getDataId(), notifyRequet.getGroup(), notifyRequet.getTenant(),
                                        clientId));
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            Loggers.CORE.warn("retry push callback task overlimit.dataId={},group={},tenant={},clientId={}",
                    notifyRequet.getDataId(), notifyRequet.getGroup(), notifyRequet.getTenant(), clientId);
        }
        
    }
}

