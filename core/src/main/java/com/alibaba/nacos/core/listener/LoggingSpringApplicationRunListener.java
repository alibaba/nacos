/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.core.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.context.event.EventPublishingRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.util.List;

import static com.alibaba.nacos.common.util.SystemUtils.LOCAL_IP;
import static com.alibaba.nacos.common.util.SystemUtils.NACOS_HOME;
import static com.alibaba.nacos.common.util.SystemUtils.STANDALONE_MODE;
import static com.alibaba.nacos.common.util.SystemUtils.readClusterConf;
import static org.springframework.boot.context.logging.LoggingApplicationListener.CONFIG_PROPERTY;
import static org.springframework.core.io.ResourceLoader.CLASSPATH_URL_PREFIX;

/**
 * Logging {@link SpringApplicationRunListener} before {@link EventPublishingRunListener} execution
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.2
 */
public class LoggingSpringApplicationRunListener implements SpringApplicationRunListener, Ordered {

    private static final String DEFAULT_NACOS_LOGBACK_LOCATION = CLASSPATH_URL_PREFIX + "META-INF/logback/nacos.xml";

    private static final Logger logger = LoggerFactory.getLogger(LoggingSpringApplicationRunListener.class);

    private static final String MODE_PROPERTY_KEY = "nacos.mode";

    private static final String LOCAL_IP_PROPERTY_KEY = "nacos.local.ip";

    private final SpringApplication application;

    private final String[] args;


    public LoggingSpringApplicationRunListener(SpringApplication application, String[] args) {
        this.application = application;
        this.args = args;
    }

    @Override
    public void starting() {
    }

    @Override
    public void environmentPrepared(ConfigurableEnvironment environment) {
        if (!environment.containsProperty(CONFIG_PROPERTY)) {
            System.setProperty(CONFIG_PROPERTY, DEFAULT_NACOS_LOGBACK_LOCATION);
            if (logger.isInfoEnabled()) {
                logger.info("There is no property named \"{}\" in Spring Boot Environment, " +
                                "and whose value is {} will be set into System's Properties", CONFIG_PROPERTY,
                        DEFAULT_NACOS_LOGBACK_LOCATION);
            }
        }

        if (STANDALONE_MODE) {
            System.setProperty(MODE_PROPERTY_KEY, "stand alone");
        } else {
            System.setProperty(MODE_PROPERTY_KEY, "cluster");
        }

        System.setProperty(LOCAL_IP_PROPERTY_KEY, LOCAL_IP);
    }

    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
        System.out.printf("Log files: %s/logs/%n", NACOS_HOME);
        System.out.printf("Conf files: %s/conf/%n", NACOS_HOME);
        System.out.printf("Data files: %s/data/%n", NACOS_HOME);

        if (!STANDALONE_MODE) {
            try {
                List<String> clusterConf = readClusterConf();
                System.out.printf("The server IP list of Nacos is %s%n", clusterConf);
            } catch (IOException e) {
                logger.error("read cluster conf fail", e);
            }
        }

        System.out.println();
    }

    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {

    }

    @Override
    public void started(ConfigurableApplicationContext context) {

    }

    @Override
    public void running(ConfigurableApplicationContext context) {

    }

    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {

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
}
