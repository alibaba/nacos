/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.lock.monitor;

import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.core.monitor.NacosMeterRegistryCenter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MetricsMonitor.
 * @author goumang.zh@alibaba-inc.com
 */
public class LockMetricsMonitor {
    
    private static final String METER_REGISTRY = NacosMeterRegistryCenter.LOCK_STABLE_REGISTRY;
    
    private static AtomicInteger grpcLockSuccess = new AtomicInteger();
    
    private static AtomicInteger grpcUnLockSuccess = new AtomicInteger();
    
    private static AtomicInteger grpcLockTotal = new AtomicInteger();
    
    private static AtomicInteger grpcUnLockTotal = new AtomicInteger();
    
    private static AtomicInteger aliveLockCount = new AtomicInteger();
    
    static {
        ImmutableTag immutableTag = new ImmutableTag("module", "lock");
        List<Tag> tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "grpcLockTotal"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, grpcLockTotal);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "grpcLockSuccess"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, grpcLockSuccess);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "grpcUnLockTotal"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, grpcUnLockTotal);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "grpcUnLockSuccess"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, grpcUnLockSuccess);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "aliveLockCount"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, aliveLockCount);
    }
    
    public static AtomicInteger getGrpcLockSuccess() {
        return grpcLockSuccess;
    }
    
    public static AtomicInteger getGrpcUnLockSuccess() {
        return grpcUnLockSuccess;
    }
    
    public static AtomicInteger getGrpcLockTotal() {
        return grpcLockTotal;
    }
    
    public static AtomicInteger getGrpcUnLockTotal() {
        return grpcUnLockTotal;
    }
    
    public static Timer getLockHandlerTimer() {
        return NacosMeterRegistryCenter
                .timer(METER_REGISTRY, "nacos_timer", "module", "lock", "name", "lockHandlerRt");
    }
    
    public static AtomicInteger getSuccessMeter(LockOperationEnum lockOperationEnum) {
        if (lockOperationEnum == LockOperationEnum.ACQUIRE) {
            return grpcLockSuccess;
        } else {
            return grpcUnLockSuccess;
        }
    }
    
    public static AtomicInteger getTotalMeter(LockOperationEnum lockOperationEnum) {
        if (lockOperationEnum == LockOperationEnum.ACQUIRE) {
            return grpcLockTotal;
        } else {
            return grpcUnLockTotal;
        }
    }
}
