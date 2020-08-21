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

import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;

import java.util.Collection;

/**
 * Nacos task execute engine.
 *
 * @author xiweng.yy
 */
public interface NacosTaskExecuteEngine<T extends NacosTask> extends Closeable {
    
    /**
     * Get Task size in execute engine.
     *
     * @return size of task
     */
    int size();
    
    /**
     * Whether the execute engine is empty.
     *
     * @return true if the execute engine has no task to do, otherwise false
     */
    boolean isEmpty();
    
    /**
     * Add task processor {@link NacosTaskProcessor} for execute engine.
     *
     * @param key           key of task
     * @param taskProcessor task processor
     */
    void addProcessor(Object key, NacosTaskProcessor taskProcessor);
    
    /**
     * Remove task processor {@link NacosTaskProcessor} form execute engine for key.
     *
     * @param key key of task
     */
    void removeProcessor(Object key);
    
    /**
     * Try to get {@link NacosTaskProcessor} by key, if non-exist, will return default processor.
     *
     * @param key key of task
     * @return task processor for task key or default processor if task processor for task key non-exist
     */
    NacosTaskProcessor getProcessor(Object key);
    
    /**
     * Get all processor key.
     *
     * @return collection of processors
     */
    Collection<Object> getAllProcessorKey();
    
    /**
     * Set default task processor. If do not find task processor by task key, use this default processor to process
     * task.
     *
     * @param defaultTaskProcessor default task processor
     */
    void setDefaultTaskProcessor(NacosTaskProcessor defaultTaskProcessor);
    
    /**
     * Add task into execute pool.
     *
     * @param key  key of task
     * @param task task
     */
    void addTask(Object key, T task);
    
    /**
     * Remove task.
     *
     * @param key key of task
     * @return nacos task
     */
    T removeTask(Object key);
    
    /**
     * Get all task keys.
     *
     * @return collection of task keys.
     */
    Collection<Object> getAllTaskKeys();
}
