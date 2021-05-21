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
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.alibaba.nacos.naming.constants.Constants.DATA_WARMUP;
import static com.alibaba.nacos.naming.constants.Constants.DISTRO_BATCH_SYNC_KEY_COUNT;
import static com.alibaba.nacos.naming.constants.Constants.DISTRO_SYNC_RETRY_DELAY;
import static com.alibaba.nacos.naming.constants.Constants.DISTRO_TASK_DISPATCH_PERIOD;
import static com.alibaba.nacos.naming.constants.Constants.EMPTY_SERVICE_CLEAN_INTERVAL;
import static com.alibaba.nacos.naming.constants.Constants.EMPTY_SERVICE_EXPIRED_TIME;
import static com.alibaba.nacos.naming.constants.Constants.EXPIRED_METADATA_CLEAN_INTERVAL;
import static com.alibaba.nacos.naming.constants.Constants.EXPIRED_METADATA_EXPIRED_TIME;
import static com.alibaba.nacos.naming.constants.Constants.EXPIRE_INSTANCE;
import static com.alibaba.nacos.naming.constants.Constants.LOAD_DATA_RETRY_DELAY_MILLIS;

/**
 * Stores some configurations for Distro protocol.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class GlobalConfig {
    
    @PostConstruct
    public void printGlobalConfig() {
        overrideDistroConfiguration();
    }
    
    private void overrideDistroConfiguration() {
        Loggers.SRV_LOG.warn("naming.distro config will be removed, please use core.protocol.distro replace.");
        Loggers.SRV_LOG.warn("Using naming.distro config to replace core.distro config");
        DistroConfig.getInstance().setSyncDelayMillis(getTaskDispatchPeriod());
        DistroConfig.getInstance().setSyncRetryDelayMillis(getSyncRetryDelay());
        DistroConfig.getInstance().setLoadDataRetryDelayMillis(getLoadDataRetryDelayMillis());
    }
    
    /**
     * Get distro task dispatch period.
     *
     * @return period
     * @deprecated Will remove on v2.1.X, see {@link DistroConfig#getSyncDelayMillis()}.
     */
    @Deprecated
    public int getTaskDispatchPeriod() {
        return EnvUtil.getProperty(DISTRO_TASK_DISPATCH_PERIOD, Integer.class, 2000);
    }
    
    /**
     * Get batch count for sync key.
     *
     * @return batch count
     * @deprecated will remove on v2.1.X.
     */
    @Deprecated
    public int getBatchSyncKeyCount() {
        return EnvUtil.getProperty(DISTRO_BATCH_SYNC_KEY_COUNT, Integer.class, 1000);
    }
    
    /**
     * Get distro task sync retry delay after sync fail.
     *
     * @return distro task sync retry delay
     * @deprecated will remove on v2.1.X, see {@link DistroConfig#getSyncRetryDelayMillis()}.
     */
    public long getSyncRetryDelay() {
        return EnvUtil.getProperty(DISTRO_SYNC_RETRY_DELAY, Long.class, 5000L);
    }
    
    public boolean isDataWarmup() {
        return EnvUtil.getProperty(DATA_WARMUP, Boolean.class, false);
    }
    
    public boolean isExpireInstance() {
        return EnvUtil.getProperty(EXPIRE_INSTANCE, Boolean.class, true);
    }
    
    /**
     * Get Distro load data retry delay after load fail.
     *
     * @return load retry delay time
     * @deprecated will remove on v2.1.X, see {@link DistroConfig#getLoadDataRetryDelayMillis()}
     */
    public long getLoadDataRetryDelayMillis() {
        return EnvUtil.getProperty(LOAD_DATA_RETRY_DELAY_MILLIS, Long.class, 60000L);
    }
    
    public static Long getEmptyServiceCleanInterval() {
        return EnvUtil.getProperty(EMPTY_SERVICE_CLEAN_INTERVAL, Long.class, 60000L);
    }
    
    public static Long getEmptyServiceExpiredTime() {
        return EnvUtil.getProperty(EMPTY_SERVICE_EXPIRED_TIME, Long.class, 60000L);
    }
    
    public static Long getExpiredMetadataCleanInterval() {
        return EnvUtil.getProperty(EXPIRED_METADATA_CLEAN_INTERVAL, Long.class, 5000L);
    }
    
    public static Long getExpiredMetadataExpiredTime() {
        return EnvUtil.getProperty(EXPIRED_METADATA_EXPIRED_TIME, Long.class, 60000L);
    }
    
}
