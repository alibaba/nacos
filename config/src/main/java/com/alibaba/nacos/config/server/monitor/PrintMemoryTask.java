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

package com.alibaba.nacos.config.server.monitor;

import com.alibaba.nacos.config.server.service.ClientTrackService;
import com.alibaba.nacos.config.server.service.ConfigCacheService;

import static com.alibaba.nacos.config.server.utils.LogUtil.MEMORY_LOG;

/**
 * Print memory task.
 *
 * @author zongtanghu
 */
public class PrintMemoryTask implements Runnable {
    
    @Override
    public void run() {
        int groupCount = ConfigCacheService.groupCount();
        int subClientCount = ClientTrackService.subscribeClientCount();
        long subCount = ClientTrackService.subscriberCount();
        MEMORY_LOG.info("groupCount = {}, subscriberClientCount = {}, subscriberCount = {}", groupCount, subClientCount,
                subCount);
        MetricsMonitor.getConfigCountMonitor().set(groupCount);
    }
}
