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

package com.alibaba.nacos.client.monitor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Handle of a client gauge, exported through the {@code nacos_monitor} meter.
 *
 * <p>The current value is kept in process, so callers can update it incrementally and read it back even when the
 * application has not added any {@code MeterRegistry} yet.
 *
 * @author Nacos
 */
public final class ClientGauge {
    
    private final AtomicLong value = new AtomicLong();
    
    ClientGauge() {
    }
    
    public void set(long newValue) {
        value.set(newValue);
    }
    
    public void increment() {
        value.incrementAndGet();
    }
    
    public void decrement() {
        value.decrementAndGet();
    }
    
    public double get() {
        return value.get();
    }
}
