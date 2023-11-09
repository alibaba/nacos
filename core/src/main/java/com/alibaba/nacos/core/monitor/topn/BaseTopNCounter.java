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
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Nacos base topN counter.
 *
 * @author xiweng.yy
 */
public abstract class BaseTopNCounter<T> {
    
    protected ConcurrentMap<T, AtomicInteger> dataCount;
    
    BaseTopNCounter() {
        dataCount = new ConcurrentHashMap<>();
    }
    
    /**
     * Get topN counter by PriorityQueue.
     *
     * @param topN topN
     * @return topN counter
     */
    public List<Pair<String, AtomicInteger>> getTopNCounter(int topN) {
        if (!checkEnabled()) {
            return Collections.emptyList();
        }
        Queue<Pair<String, AtomicInteger>> queue = new PriorityQueue<>(topN + 1,
                Comparator.comparingInt(o -> o.getSecond().get()));
        ConcurrentMap<T, AtomicInteger> snapshot = dataCount;
        dataCount = new ConcurrentHashMap<>();
        for (T t : snapshot.keySet()) {
            queue.add(Pair.with(keyToString(t), snapshot.get(t)));
        }
        return queue.stream().limit(topN).collect(Collectors.toList());
    }
    
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
