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

package com.alibaba.nacos.naming.constants;

import java.util.concurrent.TimeUnit;

/**
 * Naming module code starts with 20001.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class Constants {
    
    public static final String SERVICE_METADATA = "naming_service_metadata";
    
    public static final String INSTANCE_METADATA = "naming_instance_metadata";
    
    public static final String OLD_NAMING_RAFT_GROUP = "naming";
    
    public static final String NAMING_PERSISTENT_SERVICE_GROUP = "naming_persistent_service";
    
    public static final String NAMING_PERSISTENT_SERVICE_GROUP_V2 = "naming_persistent_service_v2";
    
    public static final String NACOS_NAMING_USE_NEW_RAFT_FIRST = "nacos.naming.use-new-raft.first";
    
    /**
     * Time interval to clear empty services, unit: millisecond. default: 60000 ms.
     */
    public static final String EMPTY_SERVICE_CLEAN_INTERVAL = "nacos.naming.clean.empty-service.interval";
    
    /**
     * Expiration time of empty service, unit: millisecond. default: 60000 ms.
     */
    public static final String EMPTY_SERVICE_EXPIRED_TIME = "nacos.naming.clean.empty-service.expired-time";
    
    /**
     * Time interval to clear expired metadata, unit: millisecond. default: 5000 ms.
     */
    public static final String EXPIRED_METADATA_CLEAN_INTERVAL = "nacos.naming.clean.expired-metadata.interval";
    
    /**
     * Expiration time of expired metadata, unit: millisecond. default: 60000 ms.
     */
    public static final String EXPIRED_METADATA_EXPIRED_TIME = "nacos.naming.clean.expired-metadata.expired-time";
    
    /**
     * Task time interval between twice processing, unit is millisecond. default: 2000 ms.
     */
    public static final String DISTRO_TASK_DISPATCH_PERIOD = "nacos.naming.distro.taskDispatchPeriod";
    
    /**
     * The batch size of the key that distro combined  delay task for http. default: 1000.
     */
    public static final String DISTRO_BATCH_SYNC_KEY_COUNT = "nacos.naming.distro.batchSyncKeyCount";
    
    /**
     * Task time interval between twice processing, unit is millisecond. default: 5000ms
     */
    public static final String DISTRO_SYNC_RETRY_DELAY = "nacos.naming.distro.syncRetryDelay";
    
    /**
     * default: false.
     */
    public static final String DATA_WARMUP = "nacos.naming.data.warmup";
    
    /**
     * default : true.
     */
    public static final String EXPIRE_INSTANCE = "nacos.naming.expireInstance";
    
    /**
     * default 60000L.
     */
    public static final String LOAD_DATA_RETRY_DELAY_MILLIS = "nacos.naming.clean.loadDataRetryDelayMillis";
    
    /**
     * UDP max retry times.
     */
    public static final int UDP_MAX_RETRY_TIMES = 1;
    
    /**
     * The Nanoseconds for receive UDP ack time out.
     */
    public static final long ACK_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10L);
    
    /**
     * The Milliseconds for push timeout.
     */
    public static final long DEFAULT_PUSH_TIMEOUT_MILLS = TimeUnit.SECONDS.toNanos(3L);
    
    /**
     * The custom instance id key.
     */
    public static final String CUSTOM_INSTANCE_ID = "customInstanceId";
    
    /**
     * The weight of instance according to instance self publish.
     */
    public static final String PUBLISH_INSTANCE_WEIGHT = "publishInstanceWeight";
    
    /**
     * The weight of instance according to instance self publish.
     */
    public static final double DEFAULT_INSTANCE_WEIGHT = 1.0D;
    
    /**
     * Whether enabled for instance according to instance self publish.
     */
    public static final String PUBLISH_INSTANCE_ENABLE = "publishInstanceEnable";
}
