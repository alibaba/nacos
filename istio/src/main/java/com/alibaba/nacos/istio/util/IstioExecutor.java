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

package com.alibaba.nacos.istio.util;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.core.utils.ClassUtils;
import com.alibaba.nacos.istio.IstioApp;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.concurrent.*;

/**
 * @author special.fy
 */
public class IstioExecutor {

    private static final ScheduledExecutorService NACOS_RESOURCE_WATCHER = ExecutorFactory.Managed
            .newScheduledExecutorService(ClassUtils.getCanonicalName(IstioApp.class),
                    EnvUtil.getAvailableProcessors(2),
                    new NameThreadFactory("com.alibaba.nacos.istio.resource.watcher"));

    private static final ExecutorService EVENT_HANDLE_EXECUTOR = ExecutorFactory.Managed
            .newSingleExecutorService(ClassUtils.getCanonicalName(IstioApp.class),
                    new NameThreadFactory("com.alibaba.nacos.istio.event.handle"));

    private static final ExecutorService XDS_PUSH_EXECUTOR = ExecutorFactory.Managed
            .newSingleExecutorService(ClassUtils.getCanonicalName(IstioApp.class),
                    new NameThreadFactory("com.alibaba.nacos.istio.xds.push"));

    private static final ExecutorService MCP_PUSH_EXECUTOR = ExecutorFactory.Managed
            .newSingleExecutorService(ClassUtils.getCanonicalName(IstioApp.class),
                    new NameThreadFactory("com.alibaba.nacos.istio.mcp.push"));


    public static void registerNacosResourceWatcher(Runnable watcher, long initialDelay, long period) {
        NACOS_RESOURCE_WATCHER.scheduleAtFixedRate(watcher, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    public static <V> Future<V> asyncHandleEvent(Callable<V> task) {
        return EVENT_HANDLE_EXECUTOR.submit(task);
    }

    public static void asyncXdsPush(Runnable task) {
        XDS_PUSH_EXECUTOR.execute(task);
    }

    public static void asyncMcpPush(Runnable task) {
        MCP_PUSH_EXECUTOR.execute(task);
    }
}
