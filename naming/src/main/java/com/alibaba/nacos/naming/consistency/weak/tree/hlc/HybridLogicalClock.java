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

/**
 * A hybrid logical clock consists of two parts:
 * - wall time: the maximum physical time it has seen
 * - logical clock: the logical clock
 *
 * @author lostcharlie
 */
public class HybridLogicalClock {
    private long localWallTime;
    private long logicalClock;

    public long getLocalWallTime() {
        return localWallTime;
    }

    private void setLocalWallTime(long localWallTime) {
        this.localWallTime = localWallTime;
    }

    public long getLogicalClock() {
        return logicalClock;
    }

    private void setLogicalClock(long logicalClock) {
        this.logicalClock = logicalClock;
    }

    public HybridLogicalClock(long localWallTime, long logicalClock) {
        this.setLocalWallTime(localWallTime);
        this.setLogicalClock(logicalClock);
    }

    public HybridLogicalClock() {
        long localWallTime = System.currentTimeMillis();
        long logicalClock = 0;
        this.set(localWallTime, logicalClock);
    }

    public void set(long localWallTime, long logicalClock) {
        this.setLocalWallTime(localWallTime);
        this.setLogicalClock(logicalClock);
    }

    public boolean smallerThan(HybridLogicalClock another) {
        if (this.getLocalWallTime() < another.getLocalWallTime()) {
            return true;
        }
        if ((this.getLocalWallTime() == another.getLocalWallTime())
            && (this.getLogicalClock() < another.getLogicalClock())) {
            return true;
        }
        return false;
    }
}
