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

package com.alibaba.nacos.core.distributed.distro;

/**
 * Distro constants.
 *
 * @author xiweng.yy
 */
public class DistroConstants {
    
    public static final String DATA_SYNC_DELAY_MILLISECONDS = "nacos.core.protocol.distro.data.sync.delayMs";
    
    public static final long DEFAULT_DATA_SYNC_DELAY_MILLISECONDS = 1000L;
    
    public static final String DATA_SYNC_TIMEOUT_MILLISECONDS = "nacos.core.protocol.distro.data.sync.timeoutMs";
    
    public static final long DEFAULT_DATA_SYNC_TIMEOUT_MILLISECONDS = 3000L;
    
    public static final String DATA_SYNC_RETRY_DELAY_MILLISECONDS = "nacos.core.protocol.distro.data.sync.retryDelayMs";
    
    public static final long DEFAULT_DATA_SYNC_RETRY_DELAY_MILLISECONDS = 3000L;
    
    public static final String DATA_VERIFY_INTERVAL_MILLISECONDS = "nacos.core.protocol.distro.data.verify.intervalMs";
    
    public static final long DEFAULT_DATA_VERIFY_INTERVAL_MILLISECONDS = 5000L;
    
    public static final String DATA_VERIFY_TIMEOUT_MILLISECONDS = "nacos.core.protocol.distro.data.verify.timeoutMs";
    
    public static final long DEFAULT_DATA_VERIFY_TIMEOUT_MILLISECONDS = 3000L;
    
    public static final String DATA_LOAD_RETRY_DELAY_MILLISECONDS = "nacos.core.protocol.distro.data.load.retryDelayMs";
    
    public static final long DEFAULT_DATA_LOAD_RETRY_DELAY_MILLISECONDS = 30000L;
    
}
