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

package com.alibaba.nacos.core.distributed.distro;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Distro configuration.
 *
 * @author xiweng.yy
 */
@Component
public class DistroConfig {
    
    @Value("${nacos.core.protocol.distro.data.sync_delay_ms:1000}")
    private long syncDelayMillis = 1000;
    
    @Value("${nacos.core.protocol.distro.data.sync_retry_delay_ms:3000}")
    private long syncRetryDelayMillis = 3000;
    
    @Value("${nacos.core.protocol.distro.data.verify_interval_ms:5000}")
    private long verifyIntervalMillis = 5000;
    
    @Value("${nacos.core.protocol.distro.data.load_retry_delay_ms:30000}")
    private long loadDataRetryDelayMillis = 30000;
    
    public long getSyncDelayMillis() {
        return syncDelayMillis;
    }
    
    public void setSyncDelayMillis(long syncDelayMillis) {
        this.syncDelayMillis = syncDelayMillis;
    }
    
    public long getSyncRetryDelayMillis() {
        return syncRetryDelayMillis;
    }
    
    public void setSyncRetryDelayMillis(long syncRetryDelayMillis) {
        this.syncRetryDelayMillis = syncRetryDelayMillis;
    }
    
    public long getVerifyIntervalMillis() {
        return verifyIntervalMillis;
    }
    
    public void setVerifyIntervalMillis(long verifyIntervalMillis) {
        this.verifyIntervalMillis = verifyIntervalMillis;
    }
    
    public long getLoadDataRetryDelayMillis() {
        return loadDataRetryDelayMillis;
    }
    
    public void setLoadDataRetryDelayMillis(long loadDataRetryDelayMillis) {
        this.loadDataRetryDelayMillis = loadDataRetryDelayMillis;
    }
}
