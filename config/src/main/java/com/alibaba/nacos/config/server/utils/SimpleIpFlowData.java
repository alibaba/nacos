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

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.config.server.Config;
import com.alibaba.nacos.core.utils.ClassUtils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * According to IP flow control, control the number of individual IP and IP total.
 *
 * @author leiwen.zh
 */
public class SimpleIpFlowData {
    
    private AtomicInteger[] data;
    
    private int slotCount;
    
    private int averageCount;
    
    private ScheduledExecutorService timer = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(Config.class),
                    new NameThreadFactory("com.alibaba.nacos.config.flow.control.ip"));
    
    class DefaultIpFlowDataManagerTask implements Runnable {
        
        @Override
        public void run() {
            rotateSlot();
        }
        
    }
    
    public SimpleIpFlowData(int slotCount, int interval) {
        if (slotCount <= 0) {
            this.slotCount = 1;
        } else {
            this.slotCount = slotCount;
        }
        data = new AtomicInteger[slotCount];
        for (int i = 0; i < data.length; i++) {
            data[i] = new AtomicInteger(0);
        }
        timer.scheduleAtFixedRate(new DefaultIpFlowDataManagerTask(), interval, interval, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Atomically increments by one the current value.
     */
    public int incrementAndGet(String ip) {
        int index = 0;
        if (ip != null) {
            index = ip.hashCode() % slotCount;
        }
        if (index < 0) {
            index = -index;
        }
        return data[index].incrementAndGet();
    }
    
    /**
     * Rotate the slot.
     */
    public void rotateSlot() {
        int totalCount = 0;
        for (int i = 0; i < slotCount; i++) {
            totalCount += data[i].get();
            data[i].set(0);
        }
        this.averageCount = totalCount / this.slotCount;
    }
    
    public int getCurrentCount(String ip) {
        int index = 0;
        if (ip != null) {
            index = ip.hashCode() % slotCount;
        }
        if (index < 0) {
            index = -index;
        }
        return data[index].get();
    }
    
    public int getAverageCount() {
        return this.averageCount;
    }
}
