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
package com.alibaba.nacos.config.server.utils;

/**
 * Thread util
 *
 * @author Nacos
 */
public class ThreadUtil {

    /**
     * 通过内核数，算出合适的线程数；1.5-2倍cpu内核数
     *
     * @return thread count
     */
    public static int getSuitableThreadCount() {
        final int coreCount = Runtime.getRuntime().availableProcessors();
        int workerCount = 1;
        while (workerCount < coreCount * THREAD_MULTIPLER) {
            workerCount <<= 1;
        }
        return workerCount;
    }

    private final static int THREAD_MULTIPLER = 2;
}
