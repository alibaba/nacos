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

/**
 * task manage
 *
 * @author huali
 */
public abstract class AbstractTask {
    /**
     * 一个任务两次处理的间隔，单位是毫秒
     */
    private long taskInterval;

    /**
     * 任务上次被处理的时间，用毫秒表示
     */
    private long lastProcessTime;

    /**
     * merge task
     *
     * @param task task
     */
    public abstract void merge(AbstractTask task);

    public void setTaskInterval(long interval) {
        this.taskInterval = interval;
    }

    public long getTaskInterval() {
        return this.taskInterval;
    }

    public void setLastProcessTime(long lastProcessTime) {
        this.lastProcessTime = lastProcessTime;
    }

    public long getLastProcessTime() {
        return this.lastProcessTime;
    }

    /**
     * TaskManager 判断当前是否需要处理这个Task，子类可以Override这个函数实现自己的逻辑
     *
     * @return
     */
    public boolean shouldProcess() {
        return (System.currentTimeMillis() - this.lastProcessTime >= this.taskInterval);
    }

}
