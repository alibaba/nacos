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
 * Simple Flow data.
 *
 * @author Nacos
 */
public class SimpleFlowData {
    
    private int index = 0;
    
    private AtomicInteger[] data;
    
    private int average;
    
    private int slotCount;
    
    private ScheduledExecutorService timer = ExecutorFactory.Managed
            .newSingleScheduledExecutorService(ClassUtils.getCanonicalName(Config.class),
                    new NameThreadFactory("com.alibaba.nacos.config.flow.control"));
    
    public SimpleFlowData(int slotCount, int interval) {
        this.slotCount = slotCount;
        data = new AtomicInteger[slotCount];
        for (int i = 0; i < data.length; i++) {
            data[i] = new AtomicInteger(0);
        }
        timer.scheduleAtFixedRate(new Runnable() {
            
            @Override
            public void run() {
                rotateSlot();
            }
            
        }, interval, interval, TimeUnit.MILLISECONDS);
    }
    
    public int addAndGet(int count) {
        return data[index].addAndGet(count);
    }
    
    public int incrementAndGet() {
        return data[index].incrementAndGet();
    }
    
    /**
     * Rotate the slot.
     */
    public void rotateSlot() {
        int total = 0;
        
        for (int i = 0; i < slotCount; i++) {
            total += data[i].get();
        }
        
        average = total / slotCount;
        
        index = (index + 1) % slotCount;
        data[index].set(0);
    }
    
    public int getCurrentCount() {
        return data[index].get();
    }
    
    public int getAverageCount() {
        return this.average;
    }
    
    public int getSlotCount() {
        return this.slotCount;
    }
    
    public String getSlotInfo() {
        StringBuilder sb = new StringBuilder();
        
        int index = this.index + 1;
        
        for (int i = 0; i < slotCount; i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(this.data[(i + index) % slotCount].get());
        }
        return sb.toString();
    }
    
    public int getCount(int prevStep) {
        prevStep = prevStep % this.slotCount;
        int index = (this.index + this.slotCount - prevStep) % this.slotCount;
        return this.data[index].intValue();
    }
    
}
