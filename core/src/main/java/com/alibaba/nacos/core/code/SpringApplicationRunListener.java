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

package com.alibaba.nacos.core.code;

import com.alibaba.nacos.core.listener.LoggingApplicationListener;
import com.alibaba.nacos.core.listener.NacosApplicationListener;
import com.alibaba.nacos.core.listener.StartingApplicationListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.EventPublishingRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link org.springframework.boot.SpringApplicationRunListener} before {@link EventPublishingRunListener} execution.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.2
 */
public class SpringApplicationRunListener implements org.springframework.boot.SpringApplicationRunListener, Ordered {
    
    private final SpringApplication application;
    
    private final String[] args;
    
    private List<NacosApplicationListener> nacosApplicationListeners = new ArrayList<>();
    
    {
        nacosApplicationListeners.add(new LoggingApplicationListener());
        nacosApplicationListeners.add(new StartingApplicationListener());
    }
    
    public SpringApplicationRunListener(SpringApplication application, String[] args) {
        this.application = application;
        this.args = args;
    }
    
    @Override
    public void starting() {
        for (NacosApplicationListener nacosApplicationListener : nacosApplicationListeners) {
            nacosApplicationListener.starting();
        }
    }
    
    @Override
    public void environmentPrepared(ConfigurableEnvironment environment) {
        for (NacosApplicationListener nacosApplicationListener : nacosApplicationListeners) {
            nacosApplicationListener.environmentPrepared(environment);
        }
    }
    
    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
        for (NacosApplicationListener nacosApplicationListener : nacosApplicationListeners) {
            nacosApplicationListener.contextPrepared(context);
        }
    }
    
    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {
        for (NacosApplicationListener nacosApplicationListener : nacosApplicationListeners) {
            nacosApplicationListener.contextLoaded(context);
        }
    }
    
    @Override
    public void started(ConfigurableApplicationContext context) {
        for (NacosApplicationListener nacosApplicationListener : nacosApplicationListeners) {
            nacosApplicationListener.started(context);
        }
    }
    
    @Override
    public void running(ConfigurableApplicationContext context) {
        for (NacosApplicationListener nacosApplicationListener : nacosApplicationListeners) {
            nacosApplicationListener.running(context);
        }
    }
    
    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        for (NacosApplicationListener nacosApplicationListener : nacosApplicationListeners) {
            nacosApplicationListener.failed(context, exception);
        }
    }
    
    /**
     * Before {@link EventPublishingRunListener}.
     *
     * @return HIGHEST_PRECEDENCE
     */
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
