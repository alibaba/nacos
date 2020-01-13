/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.api;

import com.alibaba.nacos.api.life.LifeCycle;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * For unified management of thread pool resources, the consumer can simply call
 * the register method to {@link ThreadPoolManager#register(String, ExecutorService)} the thread pool that needs to be included in
 * the life cycle management of the resource
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ThreadPoolManager implements LifeCycle {

    private Map<String, Set<ExecutorService>> resources;

    private AtomicBoolean initialized = new AtomicBoolean(false);

    private AtomicBoolean destroyed = new AtomicBoolean(false);

    @Override
    public void init() {
        if (initialized.compareAndSet(false, true)) {
            resources = new ConcurrentHashMap<String, Set<ExecutorService>>();
        }
    }

	/**
	 * Register the thread pool resources with the resource manager
	 *
	 * @param resourceName resource name
	 * @param executor {@link ExecutorService}
	 */
	public synchronized void register(String resourceName, ExecutorService executor) {
        checkState();
        if (!resources.containsKey(resourceName)) {
            resources.put(resourceName, new LinkedHashSet<ExecutorService>());
        }
        resources.get(resourceName).add(executor);
    }

	/**
	 * Cancel the uniform lifecycle management for all threads under this resource
	 *
	 * @param resourceName resource name
	 */
	public synchronized void deregister(String resourceName) {
        checkState();
        resources.remove(resourceName);
    }

	/**
	 * Undoing the uniform lifecycle management of {@link ExecutorService} under this resource
	 *
	 * @param resourceName resource name
	 * @param executor {@link ExecutorService}
	 */
	public synchronized void deregister(String resourceName, ExecutorService executor) {
        checkState();
        if (resources.containsKey(resourceName)) {
            resources.get(resourceName).remove(executor);
        }
    }

    @Override
    public void destroy() {
        if (initialized.get() && destroyed.compareAndSet(false, true)) {
            for (Map.Entry<String, Set<ExecutorService>> entry : resources.entrySet()) {
                for (ExecutorService executorService : entry.getValue()) {
                    executorService.shutdown();
                    int retry = 3;
                    while (retry > 0) {
                        retry--;
                        try {
                            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                                executorService.shutdownNow();
                            }
                        } catch (InterruptedException ie) {
                            executorService.shutdownNow();
                        }
                    }
                }
            }
        }
    }

    // Check that the corresponding initialization state is as expected

	private void checkState() {
        if (!initialized.get()) {
            throw new IllegalStateException("Resource management must be initialized");
        }
        if (destroyed.get()) {
            throw new IllegalStateException("Resource management already destroyed");
        }
    }
}
