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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.v2;

import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.task.DistroTaskEngineHolder;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Distro component registry for v2.
 *
 * @author xiweng.yy
 */
@Component
public class DistroClientComponentRegistry {
    
    private final ServerMemberManager serverMemberManager;
    
    private final DistroProtocol distroProtocol;
    
    private final DistroComponentHolder componentHolder;
    
    private final DistroTaskEngineHolder taskEngineHolder;
    
    private final ClientManager clientManager;
    
    private final ClusterRpcClientProxy clusterRpcClientProxy;
    
    private final UpgradeJudgement upgradeJudgement;
    
    public DistroClientComponentRegistry(ServerMemberManager serverMemberManager, DistroProtocol distroProtocol,
            DistroComponentHolder componentHolder, DistroTaskEngineHolder taskEngineHolder,
            ClientManagerDelegate clientManager, ClusterRpcClientProxy clusterRpcClientProxy,
            UpgradeJudgement upgradeJudgement) {
        this.serverMemberManager = serverMemberManager;
        this.distroProtocol = distroProtocol;
        this.componentHolder = componentHolder;
        this.taskEngineHolder = taskEngineHolder;
        this.clientManager = clientManager;
        this.clusterRpcClientProxy = clusterRpcClientProxy;
        this.upgradeJudgement = upgradeJudgement;
    }
    
    /**
     * Register necessary component to distro protocol for v2 {@link com.alibaba.nacos.naming.core.v2.client.Client}
     * implement.
     */
    @PostConstruct
    public void doRegister() {
        DistroClientDataProcessor dataProcessor = new DistroClientDataProcessor(clientManager, distroProtocol,
                upgradeJudgement);
        DistroTransportAgent transportAgent = new DistroClientTransportAgent(clusterRpcClientProxy,
                serverMemberManager);
        DistroClientTaskFailedHandler taskFailedHandler = new DistroClientTaskFailedHandler(taskEngineHolder);
        componentHolder.registerDataStorage(DistroClientDataProcessor.TYPE, dataProcessor);
        componentHolder.registerDataProcessor(dataProcessor);
        componentHolder.registerTransportAgent(DistroClientDataProcessor.TYPE, transportAgent);
        componentHolder.registerFailedTaskHandler(DistroClientDataProcessor.TYPE, taskFailedHandler);
    }
}
