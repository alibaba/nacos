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
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract nacos task execute engine.
 *
 * @author xiweng.yy
 */
public abstract class AbstractNacosTaskExecuteEngine<T extends NacosTask> implements NacosTaskExecuteEngine<T> {
    
    private final Logger log;
    
    private final ScheduledExecutorService processingExecutor;
    
    private final ConcurrentHashMap<Object, NacosTaskProcessor> taskProcessors = new ConcurrentHashMap<Object, NacosTaskProcessor>();
    
    protected final ConcurrentHashMap<Object, T> tasks;
    
    protected final ReentrantLock lock = new ReentrantLock();
    
    private NacosTaskProcessor defaultTaskProcessor;
    
    public AbstractNacosTaskExecuteEngine(String name) {
        this(name, 32, null, 100L);
    }
    
    public AbstractNacosTaskExecuteEngine(String name, Logger logger) {
        this(name, 32, logger, 100L);
    }
    
    public AbstractNacosTaskExecuteEngine(String name, Logger logger, long processInterval) {
        this(name, 32, logger, processInterval);
    }
    
    public AbstractNacosTaskExecuteEngine(String name, int initCapacity, Logger logger) {
        this(name, initCapacity, logger, 100L);
    }
    
    public AbstractNacosTaskExecuteEngine(String name, int initCapacity, Logger logger, long processInterval) {
        this.log = null != logger ? logger : LoggerFactory.getLogger(AbstractNacosTaskExecuteEngine.class.getName());
        tasks = new ConcurrentHashMap<Object, T>(initCapacity);
        processingExecutor = ExecutorFactory.newSingleScheduledExecutorService(new NameThreadFactory(name));
        processingExecutor.scheduleWithFixedDelay(new ProcessRunnable(), processInterval, processInterval, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public int size() {
        lock.lock();
        try {
            return tasks.size();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return tasks.isEmpty();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void addProcessor(Object key, NacosTaskProcessor taskProcessor) {
        taskProcessors.putIfAbsent(key, taskProcessor);
    }
    
    @Override
    public void removeProcessor(Object key) {
        taskProcessors.remove(key);
    }
    
    @Override
    public NacosTaskProcessor getProcessor(Object key) {
        return taskProcessors.containsKey(key) ? taskProcessors.get(key) : defaultTaskProcessor;
    }
    
    @Override
    public Collection<Object> getAllProcessorKey() {
        return taskProcessors.keySet();
    }
    
    @Override
    public void setDefaultTaskProcessor(NacosTaskProcessor defaultTaskProcessor) {
        this.defaultTaskProcessor = defaultTaskProcessor;
    }
    
    @Override
    public T removeTask(Object key) {
        lock.lock();
        try {
            T task = tasks.get(key);
            if (null != task && task.shouldProcess()) {
                return tasks.remove(key);
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Collection<Object> getAllTaskKeys() {
        Collection<Object> keys = new HashSet<Object>();
        lock.lock();
        try {
            keys.addAll(tasks.keySet());
        } finally {
            lock.unlock();
        }
        return keys;
    }
    
    @Override
    public void shutdown() throws NacosException {
        processingExecutor.shutdown();
    }
    
    protected Logger getEngineLog() {
        return log;
    }
    
    /**
     * process tasks in execute engine.
     */
    protected abstract void processTasks();
    
    private class ProcessRunnable implements Runnable {
    
        @Override
        public void run() {
            try {
                AbstractNacosTaskExecuteEngine.this.processTasks();
            } catch (Throwable e) {
                log.error(e.toString(), e);
            }
        }
    }
}
