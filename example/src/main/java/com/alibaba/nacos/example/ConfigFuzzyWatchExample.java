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

package com.alibaba.nacos.example;

import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractFuzzyWatchEventWatcher;
import com.alibaba.nacos.api.config.listener.FuzzyWatchEventWatcher;
import com.alibaba.nacos.api.config.listener.ConfigFuzzyWatchChangeEvent;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;

/**
 * Nacos config fuzzy listen example.
 * <p>
 * Add the JVM parameter to run the NamingExample:
 * {@code -DserverAddr=${nacos.server.ip}:${nacos.server.port} -Dnamespace=${namespaceId}}
 * </p>
 * <p>
 * This example demonstrates how to use fuzzy listening for Nacos configuration.
 * </p>
 * <p>
 * Fuzzy listening allows you to monitor configuration changes that match a specified pattern.
 * </p>
 * <p>
 * In this example, we publish several configurations with names starting with "test", and then add a fuzzy listener to
 * listen for changes to configurations with names starting with "test".
 * </p>
 * <p>
 * After publishing the configurations, the example waits for a brief period, then cancels the fuzzy listening.
 * </p>
 *
 * @author stone-98
 * @date 2024/3/14
 */
public class ConfigFuzzyWatchExample {
    
    public static void main(String[] args) throws NacosException, InterruptedException {
        // Set up properties for Nacos Config Service
        Properties properties = new Properties();
        properties.setProperty("serverAddr", System.getProperty("serverAddr", "localhost"));
        properties.setProperty("namespace", System.getProperty("namespace", "public"));
        
        // Create a Config Service instance
        ConfigService configService = ConfigFactory.createConfigService(properties);
        
        int publicConfigNum = 10;
        // Publish some configurations for testing
        for (int i = 0; i < publicConfigNum; i++) {
            boolean isPublishOk = configService.publishConfig("test" + i, "DEFAULT_GROUP", "content");
            System.out.println("[publish result] " + isPublishOk);
        }
        
        // Define a fuzzy listener to handle configuration changes
        FuzzyWatchEventWatcher listener = new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(ConfigFuzzyWatchChangeEvent event) {
                System.out.println("[fuzzy listen config change]" + event.toString());
            }
        };
        
        // Add the fuzzy listener to monitor configurations starting with "test"
        configService.fuzzyWatch("test*", "DEFAULT_GROUP", listener);
        System.out.println("[Fuzzy listening started.]");
        
        // Publish more configurations to trigger the listener
        Thread.sleep(1000);
        boolean isPublishOkOne = configService.publishConfig("test-one", "DEFAULT_GROUP", "content");
        System.out.println("[publish result] " + isPublishOkOne);
        
        boolean isPublishOkTwo = configService.publishConfig("nacos-test-two", "DEFAULT_GROUP", "content");
        System.out.println("[publish result] " + isPublishOkTwo);
        
        boolean isPublishOkThree = configService.publishConfig("test", "DEFAULT_GROUP", "content");
        System.out.println("[publish result] " + isPublishOkThree);
        
        // Wait briefly before canceling the fuzzy listening
        Thread.sleep(1000);
        System.out.println("Cancel fuzzy listen...");
        
        // Sleep to keep the program running for observation
        Thread.sleep(3000);
    }
}
