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

package com.alibaba.nacos.core.listener;

import com.alibaba.nacos.core.code.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Nacos Application Listener, execute init process.
 *
 * @author horizonzy
 * @since 1.4.1
 */
public interface NacosApplicationListener {
    
    /**
     * {@link SpringApplicationRunListener#starting}.
     */
    void starting();
    
    /**
     * {@link SpringApplicationRunListener#environmentPrepared}.
     *
     * @param environment environment
     */
    void environmentPrepared(ConfigurableEnvironment environment);
    
    /**
     * {@link SpringApplicationRunListener#contextLoaded}.
     *
     * @param context context
     */
    void contextPrepared(ConfigurableApplicationContext context);
    
    /**
     * {@link SpringApplicationRunListener#contextLoaded}.
     *
     * @param context context
     */
    void contextLoaded(ConfigurableApplicationContext context);
    
    /**
     * {@link SpringApplicationRunListener#started}.
     *
     * @param context context
     */
    void started(ConfigurableApplicationContext context);
    
    /**
     * {@link SpringApplicationRunListener#running}.
     *
     * @param context context
     */
    void running(ConfigurableApplicationContext context);
    
    /**
     * {@link SpringApplicationRunListener#failed}.
     *
     * @param context   context
     * @param exception exception
     */
    void failed(ConfigurableApplicationContext context, Throwable exception);
}
