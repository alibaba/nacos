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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distro records holder.
 *
 * @author xiweng.yy
 */
public class DistroRecordsHolder {
    
    private static final DistroRecordsHolder INSTANCE = new DistroRecordsHolder();
    
    private final ConcurrentMap<String, DistroRecord> distroRecords;
    
    private DistroRecordsHolder() {
        distroRecords = new ConcurrentHashMap<>();
    }
    
    public static DistroRecordsHolder getInstance() {
        return INSTANCE;
    }
    
    public Optional<DistroRecord> getRecordIfExist(String type) {
        return Optional.ofNullable(distroRecords.get(type));
    }
    
    public DistroRecord getRecord(String type) {
        distroRecords.computeIfAbsent(type, s -> new DistroRecord(type));
        return distroRecords.get(type);
    }
    
    public long getTotalSyncCount() {
        final AtomicLong result = new AtomicLong();
        distroRecords.forEach((s, distroRecord) -> result.addAndGet(distroRecord.getTotalSyncCount()));
        return result.get();
    }
    
    public long getSuccessfulSyncCount() {
        final AtomicLong result = new AtomicLong();
        distroRecords.forEach((s, distroRecord) -> result.addAndGet(distroRecord.getSuccessfulSyncCount()));
        return result.get();
    }
    
    public long getFailedSyncCount() {
        final AtomicLong result = new AtomicLong();
        distroRecords.forEach((s, distroRecord) -> result.addAndGet(distroRecord.getFailedSyncCount()));
        return result.get();
    }
    
    public int getFailedVerifyCount() {
        final AtomicInteger result = new AtomicInteger();
        distroRecords.forEach((s, distroRecord) -> result.addAndGet(distroRecord.getFailedVerifyCount()));
        return result.get();
    }
}
