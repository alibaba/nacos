/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LockMetricsMonitorTest {
    
    @Test
    void testDefaultConstructor() {
        assertNotNull(new LockMetricsMonitor());
    }
    
    @Test
    void testBasicMeters() {
        assertNotNull(LockMetricsMonitor.getGrpcLockSuccess());
        assertNotNull(LockMetricsMonitor.getGrpcUnLockSuccess());
        assertNotNull(LockMetricsMonitor.getGrpcLockTotal());
        assertNotNull(LockMetricsMonitor.getGrpcUnLockTotal());
    }
    
    @Test
    void testTimer() {
        Timer timer = LockMetricsMonitor.getLockHandlerTimer();
        assertNotNull(timer);
    }
    
    @Test
    void testGetSuccessMeter() {
        AtomicInteger lockSuccess = LockMetricsMonitor.getGrpcLockSuccess();
        AtomicInteger unlockSuccess = LockMetricsMonitor.getGrpcUnLockSuccess();
        
        assertSame(lockSuccess, LockMetricsMonitor.getSuccessMeter(LockOperationEnum.ACQUIRE));
        assertSame(unlockSuccess, LockMetricsMonitor.getSuccessMeter(LockOperationEnum.RELEASE));
        assertNotNull(LockMetricsMonitor.getSuccessMeter(LockOperationEnum.RENEW));
        assertSame(unlockSuccess,
            LockMetricsMonitor.getSuccessMeter(LockOperationEnum.CANCEL_WAIT));
        assertSame(unlockSuccess, LockMetricsMonitor.getSuccessMeter(null));
    }
    
    @Test
    void testGetTotalMeter() {
        AtomicInteger lockTotal = LockMetricsMonitor.getGrpcLockTotal();
        AtomicInteger unlockTotal = LockMetricsMonitor.getGrpcUnLockTotal();
        
        assertSame(lockTotal, LockMetricsMonitor.getTotalMeter(LockOperationEnum.ACQUIRE));
        assertSame(unlockTotal, LockMetricsMonitor.getTotalMeter(LockOperationEnum.RELEASE));
        assertNotNull(LockMetricsMonitor.getTotalMeter(LockOperationEnum.RENEW));
        assertSame(unlockTotal, LockMetricsMonitor.getTotalMeter(LockOperationEnum.CANCEL_WAIT));
        assertSame(unlockTotal, LockMetricsMonitor.getTotalMeter(null));
    }
}
