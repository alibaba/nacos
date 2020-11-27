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

package com.alibaba.nacos.naming.core.v2.client.manager.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.core.v2.client.AbstractClient;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.misc.GlobalExecutor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Client management class common capabilities.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class BaseClientManager<C extends AbstractClient> implements ClientManager {
    
    protected final ConcurrentMap<String, C> clients = new ConcurrentHashMap<>(128);
    
    public BaseClientManager() {
        GlobalExecutor.scheduleExpiredClientCleaner(new ExpiredClientCleaner(this), 0,
                Constants.DEFAULT_HEART_BEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    private static class ExpiredClientCleaner implements Runnable {
        
        private final BaseClientManager clientManager;
        
        public ExpiredClientCleaner(BaseClientManager clientManager) {
            this.clientManager = clientManager;
        }
        
        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            for (String each : clientManager.allClientId()) {
                ConnectionBasedClient client = (ConnectionBasedClient) clientManager.getClient(each);
                if (null != client && client.isExpire(currentTime)) {
                    clientManager.clientDisconnected(each);
                }
            }
        }
    }
    
}
