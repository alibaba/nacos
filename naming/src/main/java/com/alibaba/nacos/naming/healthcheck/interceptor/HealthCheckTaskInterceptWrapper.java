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

package com.alibaba.nacos.naming.healthcheck.interceptor;

import com.alibaba.nacos.naming.healthcheck.NacosHealthCheckTask;
import com.alibaba.nacos.naming.interceptor.NacosNamingInterceptorChain;
import com.alibaba.nacos.naming.misc.Loggers;

/**
 * Health check task intercept wrapper.
 *
 * @author xiweng.yy
 */
public class HealthCheckTaskInterceptWrapper implements Runnable {
    
    private final NacosHealthCheckTask task;
    
    private final NacosNamingInterceptorChain<NacosHealthCheckTask> interceptorChain;
    
    public HealthCheckTaskInterceptWrapper(NacosHealthCheckTask task) {
        this.task = task;
        this.interceptorChain = HealthCheckInterceptorChain.getInstance();
    }
    
    @Override
    public void run() {
        try {
            interceptorChain.doInterceptor(task);
        } catch (Exception e) {
            Loggers.SRV_LOG.info("Interceptor health check task {} failed", task.getTaskId(), e);
        }
    }
}
