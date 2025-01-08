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

import com.alibaba.nacos.core.listener.startup.NacosStartUp;
import com.alibaba.nacos.core.listener.startup.NacosStartUpManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.file.Paths;

/**
 * init environment config.
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.5.0
 */
public class StartingApplicationListener implements NacosApplicationListener {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(StartingApplicationListener.class);
    
    @Override
    public void starting() {
        NacosStartUpManager.getCurrentStartUp().starting();
    }
    
    @Override
    public void environmentPrepared(ConfigurableEnvironment environment) {
        NacosStartUp currentStartUp = NacosStartUpManager.getCurrentStartUp();
        currentStartUp.makeWorkDir();
        currentStartUp.injectEnvironment(environment);
        currentStartUp.loadPreProperties(environment);
        currentStartUp.initSystemProperty();
    }
    
    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
        NacosStartUpManager.getCurrentStartUp().logStartingInfo(LOGGER);
    }
    
    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {
        NacosStartUpManager.getCurrentStartUp().customEnvironment();
    }
    
    @Override
    public void started(ConfigurableApplicationContext context) {
        NacosStartUp currentStartUp = NacosStartUpManager.getCurrentStartUp();
        currentStartUp.started();
        currentStartUp.logStarted(LOGGER);
    }
    
    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        for (NacosStartUp each : NacosStartUpManager.getReverseStartedList()) {
            each.failed(exception, context);
        }
        LOGGER.error("Startup errors : ", exception);
        LOGGER.error("Nacos failed to start, please see {} for more details.",
                Paths.get(EnvUtil.getNacosHome(), "logs/nacos.log"));
    }
}
