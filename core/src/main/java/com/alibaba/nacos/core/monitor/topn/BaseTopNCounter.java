/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.monitor.topn;

import com.alibaba.nacos.common.utils.Pair;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nacos base topN counter.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public abstract class BaseTopNCounter<T> {
    
    private final Comparator<Pair<String, AtomicInteger>> comparator;
    
    protected ConcurrentMap<T, AtomicInteger> dataCount;
    
    protected BaseTopNCounter() {
        dataCount = new ConcurrentHashMap<>();
        this.comparator = Comparator.comparingInt(value -> value.getSecond().get());
    }
    
    /**
     * Get topN counter by PriorityQueue.
     *
     * @param topN topN
     * @return topN counter
     */
    public List<Pair<String, AtomicInteger>> getTopNCounter(int topN) {
        if (!checkEnabled()) {
            reset();
            return Collections.emptyList();
        }
        ConcurrentMap<T, AtomicInteger> snapshot = dataCount;
        dataCount = new ConcurrentHashMap<>(1);
        FixedSizePriorityQueue<Pair<String, AtomicInteger>> queue = new FixedSizePriorityQueue<>(topN, comparator);
        for (T t : snapshot.keySet()) {
            queue.offer(Pair.with(keyToString(t), snapshot.get(t)));
        }
        return queue.toList();
    }
    
    /**
     * Transfer key from type T to String.
     *
     * @param t key
     * @return String
     */
    protected abstract String keyToString(T t);
    
    /**
     * Increment 1 count for target key.
     *
     * @param t key
     */
    public void increment(T t) {
        if (checkEnabled()) {
            increment(t, 1);
        }
    }
    
    /**
     * Increment specified count for target key.
     *
     * @param t     key
     * @param count count
     */
    public void increment(T t, int count) {
        if (checkEnabled()) {
            dataCount.computeIfAbsent(t, k -> new AtomicInteger(0)).addAndGet(count);
        }
    }
    
    /**
     * Directly set count for target key.
     *
     * @param t     key
     * @param count new count
     */
    public void set(T t, int count) {
        if (checkEnabled()) {
            dataCount.computeIfAbsent(t, k -> new AtomicInteger(0)).set(count);
        }
    }
    
    public void reset() {
        dataCount.clear();
    }
    
    protected boolean checkEnabled() {
        return TopNConfig.getInstance().isEnabled();
    }
}
