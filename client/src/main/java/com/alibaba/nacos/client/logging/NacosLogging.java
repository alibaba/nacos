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

package com.alibaba.nacos.client.logging;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * nacos logging.
 *
 * @author mai.jh
 */
public class NacosLogging {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosLogging.class);
    
    private NacosLoggingAdapter loggingAdapter;
    
    private NacosLoggingProperties loggingProperties;
    
    private NacosLogging() {
        initLoggingAdapter();
    }
    
    private void initLoggingAdapter() {
        Class<? extends Logger> loggerClass = LOGGER.getClass();
        for (NacosLoggingAdapter each : NacosServiceLoader.load(NacosLoggingAdapter.class)) {
            LOGGER.info("Nacos Logging Adapter: {}", each.getClass().getName());
            if (each.isEnabled() && each.isAdaptedLogger(loggerClass)) {
                LOGGER.info("Nacos Logging Adapter: {} match {} success.", each.getClass().getName(),
                        loggerClass.getName());
                loggingProperties = new NacosLoggingProperties(each.getDefaultConfigLocation());
                loggingAdapter = each;
            }
        }
        if (null == loggingAdapter) {
            LOGGER.warn("Nacos Logging don't find adapter, logging will print into application logs.");
            return;
        }
        scheduleReloadTask();
    }
    
    private void scheduleReloadTask() {
        ScheduledExecutorService reloadContextService = ExecutorFactory.Managed
                .newSingleScheduledExecutorService("Nacos-Client",
                        new NameThreadFactory("com.alibaba.nacos.client.logging"));
        reloadContextService.scheduleAtFixedRate(() -> {
            if (loggingAdapter.isNeedReloadConfiguration()) {
                loggingAdapter.loadConfiguration(loggingProperties);
            }
        }, 0, loggingProperties.getReloadInternal(), TimeUnit.SECONDS);
    }
    
    private static class NacosLoggingInstance {
        
        private static final NacosLogging INSTANCE = new NacosLogging();
    }
    
    public static NacosLogging getInstance() {
        return NacosLoggingInstance.INSTANCE;
    }
    
    /**
     * Load logging Configuration.
     */
    public void loadConfiguration() {
        try {
            if (null != loggingAdapter) {
                loggingAdapter.loadConfiguration(loggingProperties);
            }
        } catch (Throwable t) {
            LOGGER.warn("Load {} Configuration of Nacos fail, message: {}", LOGGER.getClass().getName(),
                    t.getMessage());
        }
    }
}
