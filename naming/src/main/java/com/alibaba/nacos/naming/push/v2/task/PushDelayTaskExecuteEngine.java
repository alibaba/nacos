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

package com.alibaba.nacos.naming.push.v2.task;

import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.common.task.engine.NacosDelayTaskExecuteEngine;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import com.alibaba.nacos.naming.push.v2.PushExecuteService;

/**
 * Nacos naming push delay task execute engine.
 *
 * @author xiweng.yy
 */
public class PushDelayTaskExecuteEngine extends NacosDelayTaskExecuteEngine {
    
    private final ClientManager clientManager;
    
    private final ClientServiceIndexesManager indexesManager;
    
    private final ServiceStorage serviceStorage;
    
    private final PushExecuteService pushExecuteService;
    
    public PushDelayTaskExecuteEngine(ClientManager clientManager, ClientServiceIndexesManager indexesManager,
            ServiceStorage serviceStorage, PushExecuteService pushExecuteService) {
        super(PushDelayTaskExecuteEngine.class.getSimpleName(), Loggers.PUSH);
        this.clientManager = clientManager;
        this.indexesManager = indexesManager;
        this.serviceStorage = serviceStorage;
        this.pushExecuteService = pushExecuteService;
        setDefaultTaskProcessor(new PushDelayTaskProcessor(this));
    }
    
    public ClientManager getClientManager() {
        return clientManager;
    }
    
    public ClientServiceIndexesManager getIndexesManager() {
        return indexesManager;
    }
    
    public ServiceStorage getServiceStorage() {
        return serviceStorage;
    }
    
    public PushExecuteService getPushExecuteService() {
        return pushExecuteService;
    }
    
    private static class PushDelayTaskProcessor implements NacosTaskProcessor {
        
        private final PushDelayTaskExecuteEngine executeEngine;
        
        public PushDelayTaskProcessor(PushDelayTaskExecuteEngine executeEngine) {
            this.executeEngine = executeEngine;
        }
        
        @Override
        public boolean process(NacosTask task) {
            Service service = ((PushDelayTask) task).getService();
            NamingExecuteTaskDispatcher.getInstance()
                    .dispatchAndExecuteTask(service, new PushExecuteTask(service, executeEngine));
            return true;
        }
    }
}
