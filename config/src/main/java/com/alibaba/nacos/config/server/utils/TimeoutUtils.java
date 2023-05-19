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

package com.alibaba.nacos.config.server.utils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A utility class that handles timeouts and is used by the client to retrieve the total timeout of the data. After
 * obtaining the data from the network,totalTime is accumulated. Before obtaining the data from the network, check
 * whether the totalTime is greater than totalTimeout. If yes, it indicates the totalTimeout
 *
 * @author leiwen.zh
 */
public class TimeoutUtils {
    
    /**
     * Total time to get the data of consumption, the unit of ms.
     */
    private final AtomicLong totalTime = new AtomicLong(0L);
    
    private volatile long lastResetTime;
    
    private volatile boolean initialized = false;
    
    /**
     * Total timeout to get data, the unit of ms.
     */
    private long totalTimeout;
    
    /**
     * The cumulative expiration time of the time consumed by fetching the data, the unit of ms.
     */
    private long invalidThreshold;
    
    public TimeoutUtils(long totalTimeout, long invalidThreshold) {
        this.totalTimeout = totalTimeout;
        this.invalidThreshold = invalidThreshold;
    }
    
    /**
     * Init last reset time.
     */
    public synchronized void initLastResetTime() {
        if (initialized) {
            return;
        }
        lastResetTime = System.currentTimeMillis();
        initialized = true;
    }
    
    /**
     * Cumulative total time.
     */
    public void addTotalTime(long time) {
        totalTime.addAndGet(time);
    }
    
    /**
     * Is timeout.
     */
    public boolean isTimeout() {
        return totalTime.get() > this.totalTimeout;
    }
    
    /**
     * Clean the total time.
     */
    public void resetTotalTime() {
        if (isTotalTimeExpired()) {
            totalTime.set(0L);
            lastResetTime = System.currentTimeMillis();
        }
    }
    
    public AtomicLong getTotalTime() {
        return totalTime;
    }
    
    private boolean isTotalTimeExpired() {
        return System.currentTimeMillis() - lastResetTime > this.invalidThreshold;
    }
}
