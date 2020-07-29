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

package com.alibaba.nacos.naming.remote.task;

import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.remote.RemotingConnection;

import java.util.Set;

/**
 * Renew instance beat task.
 *
 * @author xiweng.yy
 */
public class RenewInstanceBeatTask implements Runnable {
    
    private final RemotingConnection remotingConnection;
    
    private final ServiceManager serviceManager;
    
    public RenewInstanceBeatTask(RemotingConnection remotingConnection, ServiceManager serviceManager) {
        this.remotingConnection = remotingConnection;
        this.serviceManager = serviceManager;
    }
    
    @Override
    public void run() {
        for (String each : remotingConnection.getInstanceIndex().keySet()) {
            Set<Instance> instances = remotingConnection.getInstanceIndex().get(each);
            Service service = serviceManager.getService(KeyBuilder.getNamespace(each), KeyBuilder.getServiceName(each));
            for (Instance actual : service.allIPs(true)) {
                if (instances.contains(actual)) {
                    actual.setHealthy(true);
                    actual.setLastBeat(System.currentTimeMillis());
                }
            }
        }
    }
}
