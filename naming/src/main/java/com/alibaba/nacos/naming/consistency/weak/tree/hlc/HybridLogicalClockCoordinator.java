/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.weak.tree.hlc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author lostcharlie
 */
@Component
public class HybridLogicalClockCoordinator {
    private HybridLogicalClock current;
    private long maxOffset;

    public HybridLogicalClock getCurrent() {
        return current;
    }

    private void setCurrent(HybridLogicalClock current) {
        this.current = current;
    }

    public long getMaxOffset() {
        return maxOffset;
    }

    @Value("${nacos.naming.tree.hlc.maxOffset:500}")
    private void setMaxOffset(long maxOffset) {
        this.maxOffset = maxOffset;
    }

    public HybridLogicalClockCoordinator() {
        this.setCurrent(new HybridLogicalClock());
    }

    public synchronized HybridLogicalClock advance() {
        return null;
    }

    public synchronized HybridLogicalClock adjust(HybridLogicalClock remoteClock) {
        return null;
    }

    public boolean isHappenBefore(HybridLogicalClock former, HybridLogicalClock latter) {
        return false;
    }

    public boolean isConcurrent(HybridLogicalClock former, HybridLogicalClock latter) {
        return false;
    }
}
