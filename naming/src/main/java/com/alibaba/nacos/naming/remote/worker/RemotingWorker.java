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

package com.alibaba.nacos.naming.remote.worker;

import com.alibaba.nacos.common.lifecycle.Closeable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Remoting worker.
 *
 * @author xiweng.yy
 */
public final class RemotingWorker implements Closeable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RemotingWorker.class);
    
    private static final String SEPARATOR = "_";
    
    private static final int QUEUE_CAPACITY = 50000;
    
    private final BlockingQueue<Runnable> queue;
    
    private final String name;
    
    private final InnerWorker worker;
    
    public RemotingWorker(final int mod, final int total) {
        name = getClass().getName() + "_" + mod + "%" + total;
        queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        worker = new InnerWorker(name);
        worker.start();
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * Execute task.
     */
    public void execute(Runnable task) {
        putTask(task);
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
    
    @Override
    public void shutdown() {
        worker.shutdown();
        queue.clear();
    }
    
    /**
     * Real worker thread.
     */
    private class InnerWorker extends Thread implements Closeable {
        
        private volatile boolean start = true;
        
        InnerWorker(String name) {
            setDaemon(false);
            setName(name);
        }
        
        @Override
        public void run() {
            while (start) {
                try {
                    Runnable task = queue.take();
                    long begin = System.currentTimeMillis();
                    task.run();
                    long duration = System.currentTimeMillis() - begin;
                    if (duration > 1000L) {
                        LOGGER.warn("it takes {}ms to run task {}", duration, task);
                    }
                } catch (Throwable e) {
                    LOGGER.error("[remoting-worker-error] " + e.toString(), e);
                }
            }
        }
        
        @Override
        public void shutdown() {
            start = false;
        }
    }
}
