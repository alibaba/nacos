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

package com.alibaba.nacos.maintainer.client.executor;

import com.alibaba.nacos.maintainer.client.utils.JustForTest;
import com.alibaba.nacos.maintainer.client.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * // TODO Access Metric.
 *
 * <p>For unified management of thread pool resources, the consumer can simply call the register method to {@link
 * ThreadPoolManager#register(String, String, ExecutorService)} the thread pool that needs to be included in the life
 * cycle management of the resource
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ThreadPoolManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPoolManager.class);
    
    private Map<String, Map<String, Set<ExecutorService>>> resourcesManager;
    
    private static final ThreadPoolManager INSTANCE = new ThreadPoolManager();
    
    private static final AtomicBoolean CLOSED = new AtomicBoolean(false);
    
    static {
        INSTANCE.init();
        ThreadUtils.addShutdownHook(new Thread(() -> {
            LOGGER.info("[ThreadPoolManager] Start destroying ThreadPool");
            shutdown();
            LOGGER.info("[ThreadPoolManager] Completed destruction of ThreadPool");
        }));
    }
    
    public static ThreadPoolManager getInstance() {
        return INSTANCE;
    }
    
    private ThreadPoolManager() {
    }
    
    private void init() {
        resourcesManager = new ConcurrentHashMap<>(8);
    }
    
    /**
     * Register the thread pool resources with the resource manager.
     *
     * @param namespace namespace name
     * @param group     group name
     * @param executor  {@link ExecutorService}
     */
    public void register(String namespace, String group, ExecutorService executor) {
        resourcesManager.compute(namespace, (namespaceKey, map) -> {
            if (map == null) {
                map = new HashMap<>(8);
            }
            map.computeIfAbsent(group, groupKey -> new HashSet<>()).add(executor);
            return map;
        });
    }
    
    /**
     * Cancel the uniform lifecycle management for all threads under this resource.
     *
     * @param namespace namespace name
     * @param group     group name
     */
    public void deregister(String namespace, String group) {
        resourcesManager.computeIfPresent(namespace, (key, map) -> {
            map.remove(group);
            return map;
        });
    }
    
    /**
     * Undoing the uniform lifecycle management of {@link ExecutorService} under this resource.
     *
     * @param namespace namespace name
     * @param group     group name
     * @param executor  {@link ExecutorService}
     */
    public void deregister(String namespace, String group, ExecutorService executor) {
        resourcesManager.computeIfPresent(namespace, (namespaceKey, map) -> {
            map.computeIfPresent(group, (groupKey, set) -> {
                set.remove(executor);
                return set;
            });
            return map;
        });
    }
    
    /**
     * Destroys all thread pool resources under this namespace.
     *
     * @param namespace namespace
     */
    public void destroy(final String namespace) {
        Map<String, Set<ExecutorService>> map = resourcesManager.remove(namespace);
        if (map != null) {
            for (Set<ExecutorService> set : map.values()) {
                for (ExecutorService executor : set) {
                    ThreadUtils.shutdownThreadPool(executor);
                }
                set.clear();
            }
            map.clear();
        }
    }
    
    /**
     * This namespace destroys all thread pool resources under the grouping.
     *
     * @param namespace namespace
     * @param group     group
     */
    public void destroy(final String namespace, final String group) {
        resourcesManager.computeIfPresent(namespace, (namespaceKey, map) -> {
            map.computeIfPresent(group, (groupKey, set) -> {
                for (ExecutorService executor : set) {
                    ThreadUtils.shutdownThreadPool(executor);
                }
                set.clear();
                return null;
            });
            return map;
        });
    }
    
    /**
     * Shutdown thread pool manager.
     */
    public static void shutdown() {
        if (!CLOSED.compareAndSet(false, true)) {
            return;
        }
        Set<String> namespaces = INSTANCE.resourcesManager.keySet();
        for (String namespace : namespaces) {
            INSTANCE.destroy(namespace);
        }
    }
    
    @JustForTest
    public Map<String, Map<String, Set<ExecutorService>>> getResourcesManager() {
        return resourcesManager;
    }
}
