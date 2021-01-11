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

package com.alibaba.nacos.core.remote.control;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.atomic.AtomicLong;

/**
 * tps record.
 *
 * @author liuzunfei
 * @version $Id: TpsRecorder.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
public class TpsRecorder {
    
    private long maxTps = -1;
    
    /**
     * monitor/intercept.
     */
    private String monitorType = "";
    
    /**
     * second count.
     */
    private Cache<String, AtomicLong> tps = CacheBuilder.newBuilder().maximumSize(10).build();
    
    public AtomicLong getTps(String second) {
        AtomicLong atomicLong = tps.getIfPresent(second);
        if (atomicLong != null) {
            return atomicLong;
        }
        synchronized (tps) {
            if (tps.getIfPresent(second) == null) {
                tps.put(second, new AtomicLong());
            }
            return tps.getIfPresent(second);
        }
    }
    
    private String second(long timeStamp) {
        String timeStampStr = String.valueOf(timeStamp);
        return timeStampStr.substring(0, timeStampStr.length() - 3);
    }
    
    protected void checkSecond(long timeStamp) {
        getTps(second(timeStamp));
    }
    
    public AtomicLong getCurrentTps() {
        return getTps(second(System.currentTimeMillis()));
    }
    
    public long getMaxTps() {
        return maxTps;
    }
    
    public void setMaxTps(long maxTps) {
        this.maxTps = maxTps;
    }
    
    public boolean isInterceptMode() {
        return "intercept".equals(this.monitorType);
    }
}
