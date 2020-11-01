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
import com.alibaba.nacos.naming.interceptor.NacosNamingInterceptor;
import com.alibaba.nacos.naming.interceptor.NacosNamingInterceptorChain;

import java.util.LinkedList;
import java.util.List;

/**
 * Health check interceptor chain.
 *
 * @author xiweng.yy
 */
public class HealthCheckInterceptorChain implements NacosNamingInterceptorChain<NacosHealthCheckTask> {
    
    private static final HealthCheckInterceptorChain INSTANCE = new HealthCheckInterceptorChain();
    
    private final List<NacosNamingInterceptor<NacosHealthCheckTask>> interceptors;
    
    private HealthCheckInterceptorChain() {
        this.interceptors = new LinkedList<>();
    }
    
    static {
        // TODO inject and register by SPI
        INSTANCE.addInterceptor(new HealthCheckEnableInterceptor());
        INSTANCE.addInterceptor(new HealthCheckResponsibleInterceptor());
    }
    
    public static HealthCheckInterceptorChain getInstance() {
        return INSTANCE;
    }
    
    @Override
    public void addInterceptor(NacosNamingInterceptor<NacosHealthCheckTask> interceptor) {
        interceptors.add(interceptor);
    }
    
    @Override
    public void doInterceptor(NacosHealthCheckTask object) {
        for (NacosNamingInterceptor<NacosHealthCheckTask> each : interceptors) {
            if (!each.isInterceptType(object.getClass())) {
                continue;
            }
            if (each.intercept(object)) {
                return;
            }
        }
        object.afterIntercept();
    }
}
