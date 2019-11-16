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

package com.alibaba.nacos.common.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author liaochuntao
 * @date 2019/10/22 9:12 上午
 **/
public class ThreadHelper {

    private ThreadHelper() {}

    public static void invokeShutdown(ThreadPoolExecutor executor) {
        if (executor != null) {
            executor.shutdown();
        }
    }

    public static void invokeShutdown(ExecutorService executorService) {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public static boolean isShutdown(ThreadPoolExecutor executor) {
        return executor == null || executor.isShutdown();
    }

    public static boolean isShutdown(ExecutorService executor) {
        return executor == null || executor.isShutdown();
    }

}
