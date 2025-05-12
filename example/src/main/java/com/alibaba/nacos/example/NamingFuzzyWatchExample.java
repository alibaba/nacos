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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.FuzzyWatchChangeEvent;
import com.alibaba.nacos.api.naming.listener.FuzzyWatchEventWatcher;
import com.alibaba.nacos.api.naming.utils.NamingUtils;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP;

/**
 * Nacos naming fuzzy watch example.
 * <p>Add the JVM parameter to run the NamingExample:</p>
 * {@code -DserverAddr=${nacos.server.ip}:${nacos.server.port} -Dnamespace=${namespaceId}}
 *
 * @author tanyongquan
 */
public class NamingFuzzyWatchExample {
    
    public static void main(String[] args) throws NacosException, InterruptedException {
        
        Properties properties = new Properties();
        properties.setProperty("serverAddr", System.getProperty("serverAddr", "localhost"));
        properties.setProperty("namespace", System.getProperty("namespace", "public"));
        
        NamingService naming = NamingFactory.createNamingService(properties);
        
        int num = 5;
        for (int i = 1; i <= num; i++) {
            String s = "nacos.test." + i;
            naming.registerInstance(s, "11.11.11.11", 8888);
        }
        
        System.out.println(num + " instance have been registered");
        
        Executor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("test-thread");
                    return thread;
                });
        
        naming.fuzzyWatch(DEFAULT_GROUP, new FuzzyWatchEventWatcher() {
            
            //EventListener onEvent is sync to handle, If process too low in onEvent, maybe block other onEvent callback.
            //So you can override getExecutor() to async handle event.
            @Override
            public Executor getExecutor() {
                return executor;
            }
            
            @Override
            public void onEvent(FuzzyWatchChangeEvent event) {
                System.out.println(
                        "[Fuzzy-Watch-GROUP]changed service name: " + NamingUtils.getServiceKey(event.getNamespace(),
                                event.getGroupName(), event.getServiceName()));
                System.out.println("[Fuzzy-Watch-GROUP]change type: " + event.getChangeType());
            }
        });
        
        naming.fuzzyWatch("nacos.test.*", DEFAULT_GROUP, new FuzzyWatchEventWatcher() {
            
            @Override
            public Executor getExecutor() {
                return executor;
            }
            
            @Override
            public void onEvent(FuzzyWatchChangeEvent event) {
                System.out.println(
                        "[Prefix-Fuzzy-Watch]changed service name: " + NamingUtils.getServiceKey(event.getNamespace(),
                                event.getGroupName(), event.getServiceName()));
                System.out.println("[Prefix-Fuzzy-Watch]change type: " + event.getChangeType());
            }
        });
        
        naming.registerInstance("nacos.test.-1", "11.11.11.11", 8888);
        
        Thread.sleep(1000);
        
        naming.registerInstance("nacos.OTHER-PREFIX", "11.11.11.11", 8888);
        
        Thread.sleep(1000);
        
        naming.registerInstance("nacos.OTHER-GROUP", "OTHER-GROUP", "11.11.11.11", 8888);
        
        Thread.sleep(1000);
        
        for (int i = 1; i <= num; i++) {
            String s = "nacos.test." + i;
            naming.deregisterInstance(s, "11.11.11.11", 8888);
        }
        
        Thread.sleep(1000);
        
    }
}
