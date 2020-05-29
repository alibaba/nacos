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
package com.alibaba.nacos.common.executor;


import com.alibaba.nacos.common.utils.ShutdownUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * // TODO Access Metric
 *
 * For unified management of thread pool resources, the consumer can simply call
 * the register method to {@link ThreadPoolManager#register(String, String, ExecutorService)}
 * the thread pool that needs to be included in the life cycle management of the resource
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ThreadPoolManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPoolManager.class);

    private Map<String, Map<String, Set<ExecutorService>>> resourcesManager;

    private Map<String, Object> lockers = new ConcurrentHashMap<String, Object>(8);

    private static final ThreadPoolManager INSTANCE = new ThreadPoolManager();

    private static final AtomicBoolean CLOSED = new AtomicBoolean(false);

    static {
        INSTANCE.init();
		ShutdownUtils.addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
				LOGGER.warn("[ThreadPoolManager] Start destroying ThreadPool");
                shutdown();
				LOGGER.warn("[ThreadPoolManager] Destruction of the end");
            }
        }));
    }

    public static ThreadPoolManager getInstance() {
        return INSTANCE;
    }

    private ThreadPoolManager() {}

    private void init() {
        resourcesManager = new ConcurrentHashMap<String, Map<String, Set<ExecutorService>>>(8);
    }

    /**
	 * Register the thread pool resources with the resource manager
	 *
     * @param namespace namespace name
	 * @param group group name
	 * @param executor {@link ExecutorService}
	 */
	public void register(String namespace, String group, ExecutorService executor) {
		if (!resourcesManager.containsKey(namespace)) {
        	synchronized(this) {
                lockers.put(namespace, new Object());
            }
        }
        final Object monitor = lockers.get(namespace);
        synchronized (monitor) {
            Map<String, Set<ExecutorService>> map = resourcesManager.get(namespace);
            if (map == null) {
            	map = new HashMap<String, Set<ExecutorService>>(8);
            	map.put(group, new HashSet<ExecutorService>());
				map.get(group).add(executor);
				resourcesManager.put(namespace, map);
				return;
			}
            if (!map.containsKey(group)) {
                map.put(group, new HashSet<ExecutorService>());
            }
            map.get(group).add(executor);
        }
    }

	/**
	 * Cancel the uniform lifecycle management for all threads under this resource
	 *
     * @param namespace namespace name
	 * @param group group name
	 */
	public void deregister(String namespace, String group) {
        if (resourcesManager.containsKey(namespace)) {
            final Object monitor = lockers.get(namespace);
            synchronized (monitor) {
                resourcesManager.get(namespace).remove(group);
            }
        }
    }

	/**
	 * Undoing the uniform lifecycle management of {@link ExecutorService} under this resource
	 *
	 * @param namespace namespace name
	 * @param group group name
	 * @param executor {@link ExecutorService}
	 */
	public void deregister(String namespace, String group, ExecutorService executor) {
        if (resourcesManager.containsKey(namespace)) {
            final Object monitor = lockers.get(namespace);
            synchronized (monitor) {
				final Map<String, Set<ExecutorService>> subResourceMap = resourcesManager.get(namespace);
				if (subResourceMap.containsKey(group)) {
					subResourceMap.get(group).remove(executor);
				}
			}
        }
    }

	/**
	 * Destroys all thread pool resources under this namespace
	 *
	 * @param namespace namespace
	 */
	public void destroy(final String namespace) {
	    final Object monitor = lockers.get(namespace);
	    if (monitor == null) {
	        return;
        }
	    synchronized (monitor) {
	        Map<String, Set<ExecutorService>> subResource = resourcesManager.get(namespace);
			if (subResource == null) {
				return;
			}
	        for (Map.Entry<String, Set<ExecutorService>> entry : subResource.entrySet()) {
	            for (ExecutorService executor : entry.getValue()) {
					ThreadUtils.shutdownThreadPool(executor);
				}
            }
            resourcesManager.get(namespace).clear();
	        resourcesManager.remove(namespace);
        }
    }

	/**
	 * This namespace destroys all thread pool resources under the grouping
	 *
	 * @param namespace namespace
	 * @param group group
	 */
	public void destroy(final String namespace, final String group) {
		final Object monitor = lockers.get(namespace);
		if (monitor == null) {
			return;
		}
		synchronized (monitor) {
			Map<String, Set<ExecutorService>> subResource = resourcesManager.get(namespace);
			if (subResource == null) {
				return;
			}
			Set<ExecutorService> waitDestroy = subResource.get(group);
			for (ExecutorService executor : waitDestroy) {
				ThreadUtils.shutdownThreadPool(executor);
			}
			resourcesManager.get(namespace).remove(group);
		}
	}

    public static void shutdown() {
		if (!CLOSED.compareAndSet(false, true)) {
			return;
		}
	    Set<String> namespaces = INSTANCE.resourcesManager.keySet();
	    for (String namespace : namespaces) {
	        INSTANCE.destroy(namespace);
        }
    }

}
