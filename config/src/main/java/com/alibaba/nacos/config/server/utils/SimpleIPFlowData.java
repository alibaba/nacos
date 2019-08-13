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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 根据IP进行流控, 控制单个IP的数量以及IP总量
 *
 * @author leiwen.zh
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class SimpleIPFlowData {

    private AtomicInteger[] data;

    private int slotCount;

    private int averageCount;

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("nacos ip flow control thread");
            t.setDaemon(true);
            return t;
        }

    });

    class DefaultIPFlowDataManagerTask implements Runnable {

        @Override
        public void run() {
            rotateSlot();
        }

    }

    public SimpleIPFlowData(int slotCount, int interval) {
        if (slotCount <= 0) {
            this.slotCount = 1;
        } else {
            this.slotCount = slotCount;
        }
        data = new AtomicInteger[slotCount];
        for (int i = 0; i < data.length; i++) {
            data[i] = new AtomicInteger(0);
        }
        timer.scheduleAtFixedRate(new DefaultIPFlowDataManagerTask(), interval, interval, TimeUnit.MILLISECONDS);
    }

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
