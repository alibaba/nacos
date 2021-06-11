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
    
    /**
     * Max task queue size 32768.
     */
    private static final int QUEUE_CAPACITY = 1 << 15;
    
    private final Logger log;
    
    private final String name;
    
    private final BlockingQueue<Runnable> queue;
    
    private final AtomicBoolean closed;
    
    public TaskExecuteWorker(final String name, final int mod, final int total) {
        this(name, mod, total, null);
    }
    
    public TaskExecuteWorker(final String name, final int mod, final int total, final Logger logger) {
        this.name = name + "_" + mod + "%" + total;
        this.queue = new ArrayBlockingQueue<Runnable>(QUEUE_CAPACITY);
        this.closed = new AtomicBoolean(false);
        this.log = null == logger ? LoggerFactory.getLogger(TaskExecuteWorker.class) : logger;
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
            log.error(ire.toString(), ire);
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
                        log.warn("task {} takes {}ms", task, duration);
                    }
                } catch (Throwable e) {
                    log.error("[TASK-FAILED] " + e.toString(), e);
                }
            }
        }
    }
}
