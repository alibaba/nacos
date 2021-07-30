/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * rpc scheduler executor .
 *
 * @author liuzunfei
 * @version $Id: RpcScheduledExecutor.java, v 0.1 2020年09月07日 4:12 PM liuzunfei Exp $
 */
public class RpcScheduledExecutor extends ScheduledThreadPoolExecutor {
    
    public static final RpcScheduledExecutor TIMEOUT_SCHEDULER = new RpcScheduledExecutor(1,
            "com.alibaba.nacos.remote.TimerScheduler");
    
    public static final RpcScheduledExecutor COMMON_SERVER_EXECUTOR = new RpcScheduledExecutor(1,
            "com.alibaba.nacos.remote.ServerCommonScheduler");
    
    public RpcScheduledExecutor(int corePoolSize, final String threadName) {
        super(corePoolSize, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, threadName);
            }
        });
    }
    
}
