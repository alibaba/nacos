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

import org.slf4j.Logger;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Nacos start up phases.
 *
 * @author xiweng.yy
 */
public interface NacosStartUp {
    
    String CORE_START_UP_PHASE = "core";
    
    String WEB_START_UP_PHASE = "web";
    
    String CONSOLE_START_UP_PHASE = "console";
    
    String MCP_REGISTRY_START_UP_PHASE = "mcp-registry";
    
    /**
     * Current Nacos Server start up phase.
     *
     * @return {@link #CORE_START_UP_PHASE} or {@link #WEB_START_UP_PHASE} or {@link #CONSOLE_START_UP_PHASE}.
     */
    String startUpPhase();
    
    /**
     * Current Nacos Server start to stand up.
     */
    void starting();
    
    /**
     * Current Nacos Server start do make work dir if necessary.
     * @return created work dirs
     */
    default String[] makeWorkDir() {
        return new String[0];
    }
    
    /**
     * Inject Environment to Current Nacos Server if necessary.
     *
     * @param environment environment
     */
    default void injectEnvironment(ConfigurableEnvironment environment) {
    }
    
    /**
     * Load Pre Properties from Environment if necessary.
     *
     * @param environment environment
     */
    default void loadPreProperties(ConfigurableEnvironment environment) {
    }
    
    /**
     * Init System Property to current Nacos Server JVM if necessary.
     */
    default void initSystemProperty() {
    }
    
    /**
     * Log Starting Info for current Nacos Server.
     *
     * @param logger logger for print info
     */
    void logStartingInfo(Logger logger);
    
    /**
     * If current Nacos Server need use custom environment plugin, implement this method.
     */
    default void customEnvironment() {
    }
    
    /**
     * Current Nacos Server finished to stand up.
     */
    void started();
    
    /**
     * Log started info for current Nacos Server.
     *
     * @param logger logger for print info
     */
    void logStarted(Logger logger);
    
    /**
     * Current Nacos Server start up failed, close relative resources and do hints according to exception.
     *
     * @param exception exception during start up
     * @param context current application context
     */
    void failed(Throwable exception, ConfigurableApplicationContext context);
}
