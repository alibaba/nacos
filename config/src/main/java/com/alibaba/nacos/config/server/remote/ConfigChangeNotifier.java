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

import com.alibaba.nacos.api.remote.response.PushCallBack;
import com.alibaba.nacos.api.remote.response.ServerPushResponse;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.core.remote.RpcPushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ConfigChangeNotifier.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifier.java, v 0.1 2020年07月20日 3:00 PM liuzunfei Exp $
 */
@Component
public class ConfigChangeNotifier {
    
    @Autowired
    ConfigChangeListenContext configChangeListenContext;
    
    @Autowired
    private RpcPushService rpcPushService;
    
    /**
     * adaptor to config module ,when server side congif change ,invoke this method.
     *
     * @param groupKey       groupKey
     * @param notifyResponse notifyResponse
     */
    public void configDataChanged(String groupKey, final ServerPushResponse notifyResponse) {
    
        long start = System.currentTimeMillis();
        Set<String> clients = configChangeListenContext.getListeners(groupKey);
    
        if (!CollectionUtils.isEmpty(clients)) {
            for (final String client : clients) {
                rpcPushService.pushWithCallback(client, notifyResponse, new PushCallBack() {
                    @Override
                    public void onSuccess() {
                        //System.out.println("推送变更成功：" + connectionId);
                    }
                
                    @Override
                    public void onFail() {
                        //System.out.println("推送变更失败：" + client);
                        retryPush(client, notifyResponse, 3);
                    }
                
                    @Override
                    public void onTimeout() {
                        //System.out.println("推送变更超时：" + client);
                        retryPush(client, notifyResponse, 3);
                    }
                });
            
            }
        }
        long end = System.currentTimeMillis();
    
    }
    
    void retryPush(String clientId, ServerPushResponse notifyResponse, int maxRetyTimes) {
        
        int maxTimes = maxRetyTimes;
        final AtomicBoolean success = new AtomicBoolean(false);
        Object lock = new Object();
        while (maxRetyTimes > 0) {
            if (success.get()) {
                return;
            }
            maxRetyTimes--;
            rpcPushService.pushWithCallback(clientId, notifyResponse, new PushCallBack() {
                @Override
                public void onSuccess() {
                    //System.out.println("推送变更成功：" + connectionId);
                    success.set(true);
                    synchronized (lock) {
                        lock.notify();
                    }
                }
                
                @Override
                public void onFail() {
                    //System.out.println("推送变更失败：" + client);
                    synchronized (lock) {
                        lock.notify();
                    }
                }
                
                @Override
                public void onTimeout() {
                    //System.out.println("推送变更超时：" + client);
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            });
            
            synchronized (lock) {
                try {
                    lock.wait(500L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (success.get()) {
            //Success
        } else {
            //reTry fails.
        }
        
    }
}

