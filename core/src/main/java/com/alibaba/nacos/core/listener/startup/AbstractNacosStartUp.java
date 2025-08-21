/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.listener.startup;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import org.slf4j.Logger;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Abstract Nacos start up.
 *
 * @author xiweng.yy
 */
public abstract class AbstractNacosStartUp implements NacosStartUp {
    
    private final String phase;
    
    private volatile ScheduledExecutorService startLoggingScheduledExecutor;
    
    private volatile boolean starting;
    
    private volatile long startTimestamp;
    
    protected AbstractNacosStartUp(String phase) {
        this.phase = phase;
    }
    
    @Override
    public String startUpPhase() {
        return phase;
    }
    
    @Override
    public void starting() {
        starting = true;
        startTimestamp = System.currentTimeMillis();
        this.startLoggingScheduledExecutor = ExecutorFactory.newSingleScheduledExecutorService(
                new NameThreadFactory(String.format("com.alibaba.nacos.%s.nacos-starting", phase)));
    }
    
    @Override
    public void logStartingInfo(Logger logger) {
        startLoggingScheduledExecutor.scheduleWithFixedDelay(() -> {
            if (starting) {
                logger.info(String.format("%s is starting...", getPhaseNameInStartingInfo()));
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    @Override
    public void started() {
        starting = false;
        closeExecutor();
    }
    
    @Override
    public void failed(Throwable exception, ConfigurableApplicationContext context) {
        starting = false;
        closeExecutor();
        context.close();
    }
    
    protected long getStartTimestamp() {
        return startTimestamp;
    }
    
    /**
     * Get phase name in starting info.
     *
     * @return phase name
     */
    protected abstract String getPhaseNameInStartingInfo();
    
    private void closeExecutor() {
        startLoggingScheduledExecutor.shutdownNow();
    }
}
