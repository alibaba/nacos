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
package com.alibaba.nacos.cmdb.utils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * @author nkorange
 * @since 0.7.0
 */
public class UtilsAndCommons {

    private static final String NACOS_SERVER_VERSION = "/v1";

    public static final String NACOS_CMDB_CONTEXT = NACOS_SERVER_VERSION + "/cmdb";

    public static final ScheduledExecutorService GLOBAL_EXECUTOR;

    static {

        GLOBAL_EXECUTOR
                = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("nacos.cmdb.global.executor");
                t.setDaemon(true);
                return t;
            }
        });
    }
}
