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
package com.alibaba.nacos.config.server.manager;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import org.slf4j.Logger;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 用于处理一定要执行成功的任务 单线程的方式处理任务，保证任务一定被成功处理
 *
 * @author huali
 */
public final class TaskManager implements TaskManagerMBean {

    private static final Logger log = LogUtil.defaultLog;

    private final ConcurrentHashMap<String, AbstractTask> tasks = new ConcurrentHashMap<String, AbstractTask>();

    private final ConcurrentHashMap<String, TaskProcessor> taskProcessors =
        new ConcurrentHashMap<String, TaskProcessor>();

    private TaskProcessor defaultTaskProcessor;

    Thread processingThread;

    private final AtomicBoolean closed = new AtomicBoolean(true);

    private String name;


    class ProcessRunnable implements Runnable {

        @Override
        public void run() {
            while (!TaskManager.this.closed.get()) {
                try {
                    Thread.sleep(100);
                    TaskManager.this.process();
                } catch (Throwable e) {
                }
            }

        }

    }

    ReentrantLock lock = new ReentrantLock();

    Condition notEmpty = this.lock.newCondition();

    public TaskManager() {
        this(null);
    }

    public AbstractTask getTask(String type) {
        return this.tasks.get(type);
    }

    public TaskProcessor getTaskProcessor(String type) {
        return this.taskProcessors.get(type);
    }

    @SuppressWarnings("PMD.AvoidManuallyCreateThreadRule")
    public TaskManager(String name) {
        this.name = name;
        if (null != name && name.length() > 0) {
            this.processingThread = new Thread(new ProcessRunnable(), name);
        } else {
            this.processingThread = new Thread(new ProcessRunnable());
        }
        this.processingThread.setDaemon(true);
        this.closed.set(false);
        this.processingThread.start();
    }

    public int size() {
        return tasks.size();
    }

    public void close() {
        this.closed.set(true);
        this.processingThread.interrupt();
    }

    public void await() throws InterruptedException {
        this.lock.lock();
        try {
            while (!this.isEmpty()) {
                this.notEmpty.await();
            }
        } finally {
            this.lock.unlock();
        }
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        this.lock.lock();
        boolean isawait = false;
        try {
            while (!this.isEmpty()) {
                isawait = this.notEmpty.await(timeout, unit);
            }
            return isawait;
        } finally {
            this.lock.unlock();
        }
    }

    public void addProcessor(String type, TaskProcessor taskProcessor) {
        this.taskProcessors.put(type, taskProcessor);
    }

    public void removeProcessor(String type) {
        this.taskProcessors.remove(type);
    }

    public void removeTask(String type) {
        this.lock.lock();
        try {
            this.tasks.remove(type);
            MetricsMonitor.getDumpTaskMonitor().set(tasks.size());
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * 将任务加入到任务Map中
     *
     * @param type
     * @param task
     */
    public void addTask(String type, AbstractTask task) {
        this.lock.lock();
        try {
            AbstractTask oldTask = tasks.put(type, task);
            MetricsMonitor.getDumpTaskMonitor().set(tasks.size());
            if (null != oldTask) {
                task.merge(oldTask);
            }
        } finally {
            this.lock.unlock();
        }
    }

    /**
     *
     */
    protected void process() {
        for (Map.Entry<String, AbstractTask> entry : this.tasks.entrySet()) {
            AbstractTask task = null;
            this.lock.lock();
            try {
                // 获取任务
                task = entry.getValue();
                if (null != task) {
                    if (!task.shouldProcess()) {
                        // 任务当前不需要被执行，直接跳过
                        continue;
                    }
                    // 先将任务从任务Map中删除
                    this.tasks.remove(entry.getKey());
                    MetricsMonitor.getDumpTaskMonitor().set(tasks.size());
                }
            } finally {
                this.lock.unlock();
            }

            if (null != task) {
                // 获取任务处理器
                TaskProcessor processor = this.taskProcessors.get(entry.getKey());
                if (null == processor) {
                    // 如果没有根据任务类型设置的处理器，使用默认处理器
                    processor = this.getDefaultTaskProcessor();
                }
                if (null != processor) {
                    boolean result = false;
                    try {
                        // 处理任务
                        result = processor.process(entry.getKey(), task);
                    } catch (Throwable t) {
                        log.error("task_fail", "处理task失败", t);
                    }
                    if (!result) {
                        // 任务处理失败，设置最后处理时间
                        task.setLastProcessTime(System.currentTimeMillis());

                        // 将任务重新加入到任务Map中
                        this.addTask(entry.getKey(), task);
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            this.lock.lock();
            try {
                this.notEmpty.signalAll();
            } finally {
                this.lock.unlock();
            }
        }
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    public TaskProcessor getDefaultTaskProcessor() {
        this.lock.lock();
        try {
            return this.defaultTaskProcessor;
        } finally {
            this.lock.unlock();
        }
    }

    public void setDefaultTaskProcessor(TaskProcessor defaultTaskProcessor) {
        this.lock.lock();
        try {
            this.defaultTaskProcessor = defaultTaskProcessor;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public String getTaskInfos() {
        StringBuilder sb = new StringBuilder();
        for (String taskType : this.taskProcessors.keySet()) {
            sb.append(taskType).append(":");
            AbstractTask task = this.tasks.get(taskType);
            if (task != null) {
                sb.append(new Date(task.getLastProcessTime()).toString());
            } else {
                sb.append("finished");
            }
            sb.append(Constants.NACOS_LINE_SEPARATOR);
        }

        return sb.toString();
    }

    public void init() {
        try {
            ObjectName oName = new ObjectName(this.name + ":type=" + TaskManager.class.getSimpleName());
            ManagementFactory.getPlatformMBeanServer().registerMBean(this, oName);
        } catch (Exception e) {
            log.error("registerMBean_fail", "注册mbean出错", e);
        }
    }
}
