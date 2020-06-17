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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Unified thread pool creation factory, and actively create thread
 * pool resources by ThreadPoolManager for unified life cycle management
 * {@link ExecutorFactory.Managed}
 *
 * Unified thread pool creation factory without life cycle management
 * {@link ExecutorFactory}
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.ThreadPoolCreationRule")
public final class ExecutorFactory {

    public static ExecutorService newSingleExecutorService() {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        return executorService;
    }

    public static ExecutorService newSingleExecutorService(final ThreadFactory threadFactory) {
        ExecutorService executorService = Executors.newFixedThreadPool(1, threadFactory);
        return executorService;
    }

    public static ExecutorService newFixExecutorService(final int nThreads) {
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        return executorService;
    }

    public static ExecutorService newFixExecutorService(final int nThreads,
                                                        final ThreadFactory threadFactory) {
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads, threadFactory);
        return executorService;
    }

    public static ScheduledExecutorService newSingleScheduledExecutorService(final ThreadFactory threadFactory) {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, threadFactory);
        return executorService;
    }

    public static ScheduledExecutorService newScheduledExecutorService( final int nThreads,
                                                                        final ThreadFactory threadFactory) {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(nThreads, threadFactory);
        return executorService;
    }

    public static ThreadPoolExecutor newCustomerThreadExecutor(
        final int coreThreads,
        final int maxThreads,
        final long keepAliveTimeMs,
        final ThreadFactory threadFactory) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(coreThreads, maxThreads,
            keepAliveTimeMs, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            threadFactory);
        return executor;
    }
    //TODO remove Deprecated function after replace all module
    @Deprecated
    public static ExecutorService newSingleExecutorService(final String group) {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        return executorService;
    }
    @Deprecated
    public static ExecutorService newSingleExecutorService(final String group,
        final ThreadFactory threadFactory) {
        ExecutorService executorService = Executors.newFixedThreadPool(1, threadFactory);
        return executorService;
    }
    @Deprecated
    public static ExecutorService newFixExecutorService(final String group,
        final int nThreads) {
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        return executorService;
    }
    @Deprecated
    public static ExecutorService newFixExecutorService(final String group,
        final int nThreads,
        final ThreadFactory threadFactory) {
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads, threadFactory);
        return executorService;
    }
    @Deprecated
    public static ScheduledExecutorService newSingleScheduledExecutorService(final String group,
        final ThreadFactory threadFactory) {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, threadFactory);
        return executorService;
    }
    @Deprecated
    public static ScheduledExecutorService newScheduledExecutorService(final String group,
        final int nThreads,
        final ThreadFactory threadFactory) {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(nThreads, threadFactory);
        return executorService;
    }
    @Deprecated
    public static ThreadPoolExecutor newCustomerThreadExecutor(final String group,
        final int coreThreads,
        final int maxThreads,
        final long keepAliveTimeMs,
        final ThreadFactory threadFactory) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(coreThreads, maxThreads,
            keepAliveTimeMs, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            threadFactory);
        return executor;
    }

    public final static class Managed{
        public static final String DEFAULT_NAMESPACE = "nacos";

        private static final ThreadPoolManager THREAD_POOL_MANAGER = ThreadPoolManager.getInstance();

        public static ExecutorService newSingleExecutorService(final String group) {
            ExecutorService executorService = Executors.newFixedThreadPool(1);
            THREAD_POOL_MANAGER.register(DEFAULT_NAMESPACE, group, executorService);
            return executorService;
        }

        public static ExecutorService newSingleExecutorService(final String group,
            final ThreadFactory threadFactory) {
            ExecutorService executorService = Executors.newFixedThreadPool(1, threadFactory);
            THREAD_POOL_MANAGER.register(DEFAULT_NAMESPACE, group, executorService);
            return executorService;
        }

        public static ExecutorService newFixExecutorService(final String group,
            final int nThreads) {
            ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
            THREAD_POOL_MANAGER.register(DEFAULT_NAMESPACE, group, executorService);
            return executorService;
        }

        public static ExecutorService newFixExecutorService(final String group,
            final int nThreads,
            final ThreadFactory threadFactory) {
            ExecutorService executorService = Executors.newFixedThreadPool(nThreads, threadFactory);
            THREAD_POOL_MANAGER.register(DEFAULT_NAMESPACE, group, executorService);
            return executorService;
        }

        public static ScheduledExecutorService newSingleScheduledExecutorService(final String group,
            final ThreadFactory threadFactory) {
            ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, threadFactory);
            THREAD_POOL_MANAGER.register(DEFAULT_NAMESPACE, group, executorService);
            return executorService;
        }

        public static ScheduledExecutorService newScheduledExecutorService(final String group,
            final int nThreads,
            final ThreadFactory threadFactory) {
            ScheduledExecutorService executorService = Executors.newScheduledThreadPool(nThreads, threadFactory);
            THREAD_POOL_MANAGER.register(DEFAULT_NAMESPACE, group, executorService);
            return executorService;
        }

        public static ThreadPoolExecutor newCustomerThreadExecutor(final String group,
            final int coreThreads,
            final int maxThreads,
            final long keepAliveTimeMs,
            final ThreadFactory threadFactory) {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(coreThreads, maxThreads,
                keepAliveTimeMs, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(),
                threadFactory);
            THREAD_POOL_MANAGER.register(DEFAULT_NAMESPACE, group, executor);
            return executor;
        }
    }
}
