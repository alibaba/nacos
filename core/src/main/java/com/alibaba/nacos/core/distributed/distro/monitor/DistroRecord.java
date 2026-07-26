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

package com.alibaba.nacos.core.distributed.distro.monitor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distro record for monitor.
 *
 * @author xiweng.yy
 */
public class DistroRecord {
    
    private final String type;
    
    private final AtomicLong totalSyncCount;
    
    private final AtomicLong successfulSyncCount;
    
    private final AtomicLong failedSyncCount;
    
    private final AtomicInteger failedVerifyCount;
    
    public DistroRecord(String type) {
        this.type = type;
        this.totalSyncCount = new AtomicLong();
        this.successfulSyncCount = new AtomicLong();
        this.failedSyncCount = new AtomicLong();
        this.failedVerifyCount = new AtomicInteger();
    }
    
    public String getType() {
        return type;
    }
    
    public void syncSuccess() {
        successfulSyncCount.incrementAndGet();
        totalSyncCount.incrementAndGet();
    }
    
    public void syncFail() {
        failedSyncCount.incrementAndGet();
        totalSyncCount.incrementAndGet();
    }
    
    public void verifyFail() {
        failedVerifyCount.incrementAndGet();
    }
    
    public long getTotalSyncCount() {
        return totalSyncCount.get();
    }
    
    public long getSuccessfulSyncCount() {
        return successfulSyncCount.get();
    }
    
    public long getFailedSyncCount() {
        return failedSyncCount.get();
    }
    
    public int getFailedVerifyCount() {
        return failedVerifyCount.get();
    }
}
