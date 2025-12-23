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

import com.alibaba.nacos.api.plugin.PluginStateChecker;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.trace.spi.NacosTraceSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
        Optional<PluginStateChecker> checker = PluginStateCheckerHolder.getInstance();
        if (checker.isPresent()) {
            return traceSubscribers.values().stream()
                    .filter(subscriber -> {
                        boolean enabled = checker.get().isPluginEnabled(PluginType.TRACE.getType(), subscriber.getName());
                        if (!enabled) {
                            LOGGER.debug("[TracePluginManager] Plugin TRACE:{} is disabled", subscriber.getName());
                        }
                        return enabled;
                    })
                    .collect(Collectors.toSet());
        }
        return new HashSet<>(traceSubscribers.values());
    }

    /**
     * Get all trace subscribers without filtering.
     *
     * @return unmodifiable map of all trace subscribers
     */
    public Map<String, NacosTraceSubscriber> getAllPlugins() {
        return Collections.unmodifiableMap(traceSubscribers);
    }
}
