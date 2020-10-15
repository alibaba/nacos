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

package com.alibaba.nacos.common.task.engine;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos execute task execute worker.
 *
 * @author xiweng.yy
 */
public final class TaskExecuteWorker implements NacosTaskProcessor, Closeable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecuteWorker.class);
    
    /**
     * Max task queue size 32768.
     */
    private static final int QUEUE_CAPACITY = 1 << 15;
    
    private final BlockingQueue<Runnable> queue;
    
    private final String name;
    
    private final AtomicBoolean closed;
    
    public TaskExecuteWorker(final String name, final int mod, final int total) {
        this.name = name + "_" + mod + "%" + total;
        queue = new ArrayBlockingQueue<Runnable>(QUEUE_CAPACITY);
        closed = new AtomicBoolean(false);
        new InnerWorker(name).start();
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public boolean process(NacosTask task) {
        if (task instanceof AbstractExecuteTask) {
            putTask((Runnable) task);
        }
        return true;
    }
    
    private void putTask(Runnable task) {
        try {
            queue.put(task);
        } catch (InterruptedException ire) {
            LOGGER.error(ire.toString(), ire);
        }
    }
    
    public int pendingTaskCount() {
        return queue.size();
    }
    
    /**
     * Worker status.
     */
    public String status() {
        return name + ", pending tasks: " + pendingTaskCount();
    }
    
    @Override
    public void shutdown() throws NacosException {
        queue.clear();
        closed.compareAndSet(false, true);
    }
    
    /**
     * Inner execute worker.
     */
    private class InnerWorker extends Thread {
        
        InnerWorker(String name) {
            setDaemon(false);
            setName(name);
        }
        
        @Override
        public void run() {
            while (!closed.get()) {
                try {
                    Runnable task = queue.take();
                    long begin = System.currentTimeMillis();
                    task.run();
                    long duration = System.currentTimeMillis() - begin;
                    if (duration > 1000L) {
                        LOGGER.warn("distro task {} takes {}ms", task, duration);
                    }
                } catch (Throwable e) {
                    LOGGER.error("[DISTRO-FAILED] " + e.toString(), e);
                }
            }
        }
    }
}
