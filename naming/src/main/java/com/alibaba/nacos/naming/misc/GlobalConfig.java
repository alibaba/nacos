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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Stores some configurations for Distro protocol.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class GlobalConfig {
    
    private final DistroConfig distroConfig;
    
    @Value("${nacos.naming.distro.taskDispatchPeriod:2000}")
    private int taskDispatchPeriod = 2000;
    
    @Value("${nacos.naming.distro.batchSyncKeyCount:1000}")
    private int batchSyncKeyCount = 1000;
    
    @Value("${nacos.naming.distro.syncRetryDelay:5000}")
    private long syncRetryDelay = 5000L;
    
    @Value("${nacos.naming.data.warmup:false}")
    private boolean dataWarmup = false;
    
    @Value("${nacos.naming.expireInstance:true}")
    private boolean expireInstance = true;
    
    @Value("${nacos.naming.distro.loadDataRetryDelayMillis:30000}")
    private long loadDataRetryDelayMillis = 30000;
    
    public GlobalConfig(DistroConfig distroConfig) {
        this.distroConfig = distroConfig;
    }
    
    @PostConstruct
    public void printGlobalConfig() {
        Loggers.SRV_LOG.info(toString());
        overrideDistroConfiguration();
    }
    
    private void overrideDistroConfiguration() {
        distroConfig.setSyncDelayMillis(taskDispatchPeriod);
        distroConfig.setSyncRetryDelayMillis(syncRetryDelay);
        distroConfig.setLoadDataRetryDelayMillis(loadDataRetryDelayMillis);
    }
    
    public int getTaskDispatchPeriod() {
        return taskDispatchPeriod;
    }
    
    public int getBatchSyncKeyCount() {
        return batchSyncKeyCount;
    }
    
    public long getSyncRetryDelay() {
        return syncRetryDelay;
    }
    
    public boolean isDataWarmup() {
        return dataWarmup;
    }
    
    public boolean isExpireInstance() {
        return expireInstance;
    }
    
    public long getLoadDataRetryDelayMillis() {
        return loadDataRetryDelayMillis;
    }
    
    @Override
    public String toString() {
        return "GlobalConfig{" + "taskDispatchPeriod=" + taskDispatchPeriod + ", batchSyncKeyCount=" + batchSyncKeyCount
                + ", syncRetryDelay=" + syncRetryDelay + ", dataWarmup=" + dataWarmup + ", expireInstance="
                + expireInstance + ", loadDataRetryDelayMillis=" + loadDataRetryDelayMillis + '}';
    }
}
