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

import com.alibaba.nacos.core.utils.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.context.event.EventPublishingRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.core.utils.SystemUtils.FUNCTION_MODE;
import static com.alibaba.nacos.core.utils.SystemUtils.LOCAL_IP;
import static com.alibaba.nacos.core.utils.SystemUtils.NACOS_HOME;
import static com.alibaba.nacos.core.utils.SystemUtils.STANDALONE_MODE;
import static com.alibaba.nacos.core.utils.SystemUtils.readClusterConf;

/**
 * Logging starting message {@link SpringApplicationRunListener} before {@link EventPublishingRunListener} execution
 *
 * @author <a href="mailto:huangxiaoyu1018@gmail.com">hxy1991</a>
 * @since 0.5.0
 */
public class StartingSpringApplicationRunListener implements SpringApplicationRunListener, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartingSpringApplicationRunListener.class);

    private static final String MODE_PROPERTY_KEY_STAND_MODE = "nacos.mode";

    private static final String MODE_PROPERTY_KEY_FUNCTION_MODE = "nacos.function.mode";

    private static final String LOCAL_IP_PROPERTY_KEY = "nacos.local.ip";

    private ScheduledExecutorService scheduledExecutorService;

    private volatile boolean starting;

    public StartingSpringApplicationRunListener(SpringApplication application, String[] args) {

    }

    @Override
    public void starting() {
        starting = true;
    }

    @Override
    public void environmentPrepared(ConfigurableEnvironment environment) {
        if (STANDALONE_MODE) {
            System.setProperty(MODE_PROPERTY_KEY_STAND_MODE, "stand alone");
        } else {
            System.setProperty(MODE_PROPERTY_KEY_STAND_MODE, "cluster");
        }
        if (FUNCTION_MODE == null) {
           System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, "All");
        } else if(SystemUtils.FUNCTION_MODE_CONFIG.equals(FUNCTION_MODE)){
            System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, SystemUtils.FUNCTION_MODE_CONFIG);
        } else if(SystemUtils.FUNCTION_MODE_NAMING.equals(FUNCTION_MODE)) {
            System.setProperty(MODE_PROPERTY_KEY_FUNCTION_MODE, SystemUtils.FUNCTION_MODE_NAMING);
        }


        System.setProperty(LOCAL_IP_PROPERTY_KEY, LOCAL_IP);
    }

    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
        logClusterConf();

        logStarting();
    }

    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {

    }

    @Override
    public void started(ConfigurableApplicationContext context) {
        starting = false;

        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
        }

        logFilePath();

        LOGGER.info("Nacos started successfully in {} mode.", System.getProperty(MODE_PROPERTY_KEY_STAND_MODE));
    }

    @Override
    public void running(ConfigurableApplicationContext context) {

    }

    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        starting = false;

        logFilePath();

        LOGGER.error("Nacos failed to start, please see {}/logs/nacos.log for more details.", NACOS_HOME);
    }

    /**
     * Before {@link EventPublishingRunListener}
     *
     * @return HIGHEST_PRECEDENCE
     */
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    private void logClusterConf() {
        if (!STANDALONE_MODE) {
            try {
                List<String> clusterConf = readClusterConf();
                LOGGER.info("The server IP list of Nacos is {}", clusterConf);
            } catch (IOException e) {
                LOGGER.error("read cluster conf fail", e);
            }
        }
    }

    private void logFilePath() {
        String[] dirNames = new String[]{"logs", "conf", "data"};
        for (String dirName: dirNames) {
            LOGGER.info("Nacos {} files: {}{}{}{}", dirName,  NACOS_HOME, File.separatorChar, dirName, File.separatorChar);
        }
    }

    private void logStarting() {
        if (!STANDALONE_MODE) {

            scheduledExecutorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "nacos-starting");
                    thread.setDaemon(true);
                    return thread;
                }
            });

            scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    if (starting) {
                        LOGGER.info("Nacos is starting...");
                    }
                }
            }, 1, 1, TimeUnit.SECONDS);
        }
    }
}
