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

import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.logging.NacosLoggingAdapter;
import com.alibaba.nacos.common.logging.NacosLoggingAdapterBuilder;
import com.alibaba.nacos.common.logging.NacosLoggingProperties;
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
        for (NacosLoggingAdapterBuilder each : NacosServiceLoader.load(NacosLoggingAdapterBuilder.class)) {
            LOGGER.info("Nacos Logging Adapter Builder: {}", each.getClass().getName());
            NacosLoggingAdapter tempLoggingAdapter = buildLoggingAdapterFromBuilder(each);
            if (isAdaptLogging(tempLoggingAdapter, loggerClass)) {
                LOGGER.info("Nacos Logging Adapter: {} match {} success.", tempLoggingAdapter.getClass().getName(),
                        loggerClass.getName());
                loggingProperties = new NacosLoggingProperties(tempLoggingAdapter.getDefaultConfigLocation(),
                        NacosClientProperties.PROTOTYPE.asProperties());
                loggingAdapter = tempLoggingAdapter;
            }
        }
        if (null == loggingAdapter) {
            LOGGER.warn("Nacos Logging don't find adapter, logging will print into application logs.");
            return;
        }
        scheduleReloadTask();
    }
    
    private NacosLoggingAdapter buildLoggingAdapterFromBuilder(NacosLoggingAdapterBuilder builder) {
        try {
            return builder.build();
        } catch (Throwable e) {
            LOGGER.warn("Build Nacos Logging Adapter failed: {}", e.getMessage());
            return null;
        }
    }
    
    private boolean isAdaptLogging(NacosLoggingAdapter loggingAdapter, Class<? extends Logger> loggerClass) {
        return null != loggingAdapter && loggingAdapter.isEnabled() && loggingAdapter.isAdaptedLogger(loggerClass);
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
