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

package com.alibaba.nacos.naming.healthcheck.heartbeat;

import com.alibaba.nacos.naming.interceptor.NacosNamingInterceptor;
import com.alibaba.nacos.naming.interceptor.NacosNamingInterceptorChain;

import java.util.LinkedList;
import java.util.List;

/**
 * Instance beat check interceptor chain.
 *
 * @author xiweng.yy
 */
public class InstanceBeatCheckTaskInterceptorChain implements NacosNamingInterceptorChain<InstanceBeatCheckTask> {
    
    private static final InstanceBeatCheckTaskInterceptorChain INSTANCE = new InstanceBeatCheckTaskInterceptorChain();
    
    private final List<NacosNamingInterceptor<InstanceBeatCheckTask>> interceptors;
    
    private InstanceBeatCheckTaskInterceptorChain() {
        this.interceptors = new LinkedList<>();
    }
    
    static {
        // TODO inject and register by SPI
        INSTANCE.addInterceptor(new ServiceEnableBeatCheckInterceptor());
        INSTANCE.addInterceptor(new InstanceEnableBeatCheckInterceptor());
    }
    
    public static InstanceBeatCheckTaskInterceptorChain getInstance() {
        return INSTANCE;
    }
    
    @Override
    public void addInterceptor(NacosNamingInterceptor<InstanceBeatCheckTask> interceptor) {
        interceptors.add(interceptor);
    }
    
    @Override
    public void doInterceptor(InstanceBeatCheckTask object) {
        for (NacosNamingInterceptor<InstanceBeatCheckTask> each : interceptors) {
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
