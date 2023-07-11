/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.trace;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.trace.spi.NacosTraceSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos trace event subscriber manager.
 *
 * @author xiweng.yy
 */
public class NacosTracePluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosTracePluginManager.class);
    
    private static final NacosTracePluginManager INSTANCE = new NacosTracePluginManager();
    
    private final Map<String, NacosTraceSubscriber> traceSubscribers;
    
    private NacosTracePluginManager() {
        this.traceSubscribers = new ConcurrentHashMap<>();
        Collection<NacosTraceSubscriber> plugins = NacosServiceLoader.load(NacosTraceSubscriber.class);
        for (NacosTraceSubscriber each : plugins) {
            this.traceSubscribers.put(each.getName(), each);
            LOGGER.info("[TracePluginManager] Load NacosTraceSubscriber({}) name({}) successfully.", each.getClass(),
                    each.getName());
        }
    }
    
    public static NacosTracePluginManager getInstance() {
        return INSTANCE;
    }
    
    public Collection<NacosTraceSubscriber> getAllTraceSubscribers() {
        return new HashSet<>(traceSubscribers.values());
    }
}
