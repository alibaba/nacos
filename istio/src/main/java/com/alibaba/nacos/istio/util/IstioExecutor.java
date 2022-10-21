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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author special.fy
 */
public class IstioExecutor {
    private static final ExecutorService EVENT_HANDLE_EXECUTOR = ExecutorFactory.Managed
            .newSingleExecutorService(ClassUtils.getCanonicalName(IstioApp.class),
                new NameThreadFactory("com.alibaba.nacos.istio.event.handle"));
    
    private static final ExecutorService PUSH_CHANGE_EXECUTOR = ExecutorFactory.Managed
            .newSingleExecutorService(ClassUtils.getCanonicalName(IstioApp.class),
                    new NameThreadFactory("com.alibaba.nacos.istio.pushchange.debounce"));
    
    private static final ExecutorService CYCLE_DEBOUNCE_EXECUTOR = ExecutorFactory.Managed
            .newSingleExecutorService(ClassUtils.getCanonicalName(IstioApp.class),
                    new NameThreadFactory("com.alibaba.nacos.istio.cycle.debounce"));

    public static <V> Future<V> asyncHandleEvent(Callable<V> task) {
        return EVENT_HANDLE_EXECUTOR.submit(task);
    }
    
    public static <V> Future<V> debouncePushChange(Callable<V> debounce) {
        return PUSH_CHANGE_EXECUTOR.submit(debounce);
    }
    
    public static void cycleDebounce(Runnable toNotify) {
        CYCLE_DEBOUNCE_EXECUTOR.submit(toNotify);
    }
}
