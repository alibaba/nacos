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

package com.alibaba.nacos.core.distributed.distro.task.verify;

import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.core.distributed.distro.component.DistroCallback;
import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.utils.Loggers;

import java.util.List;

/**
 * Execute distro verify task.
 *
 * @author xiweng.yy
 */
public class DistroVerifyExecuteTask extends AbstractExecuteTask {
    
    private final DistroTransportAgent transportAgent;
    
    private final List<DistroData> verifyData;
    
    private final String targetServer;
    
    private final String resourceType;
    
    public DistroVerifyExecuteTask(DistroTransportAgent transportAgent, List<DistroData> verifyData,
            String targetServer, String resourceType) {
        this.transportAgent = transportAgent;
        this.verifyData = verifyData;
        this.targetServer = targetServer;
        this.resourceType = resourceType;
    }
    
    @Override
    public void run() {
        for (DistroData each : verifyData) {
            try {
                if (transportAgent.supportCallbackTransport()) {
                    doSyncVerifyDataWithCallback(each);
                } else {
                    doSyncVerifyData(each);
                }
            } catch (Exception e) {
                Loggers.DISTRO
                        .error("[DISTRO-FAILED] verify data for type {} to {} failed.", resourceType, targetServer, e);
            }
        }
    }
    
    private void doSyncVerifyDataWithCallback(DistroData data) {
        transportAgent.syncVerifyData(data, targetServer, new DistroVerifyCallback());
    }
    
    private void doSyncVerifyData(DistroData data) {
        transportAgent.syncVerifyData(data, targetServer);
    }
    
    /**
     * TODO add verify monitor.
     */
    private class DistroVerifyCallback implements DistroCallback {
        
        @Override
        public void onSuccess() {
            if (Loggers.DISTRO.isDebugEnabled()) {
                Loggers.DISTRO.debug("[DISTRO] verify data for type {} to {} success", resourceType, targetServer);
            }
        }
        
        @Override
        public void onFailed(Throwable throwable) {
            if (Loggers.DISTRO.isDebugEnabled()) {
                Loggers.DISTRO
                        .debug("[DISTRO-FAILED] verify data for type {} to {} failed.", resourceType, targetServer,
                                throwable);
            }
        }
    }
}
