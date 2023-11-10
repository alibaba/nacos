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

package com.alibaba.nacos.example;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.AbstractEventListener;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.NamingEvent;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Nacos naming example.
 * <p>Add the JVM parameter to run the NamingExample:</p>
 * {@code -DserverAddr=${nacos.server.ip}:${nacos.server.port} -Dnamespace=${namespaceId}}
 *
 * @author nkorange
 */
public class NamingExample {
    
    private static final String INSTANCE_SERVICE_NAME = "nacos.test.3";
    
    private static final String INSTANCE_IP = "11.11.11.11";
    
    private static final int INSTANCE_PORT = 8888;
    
    private static final String INSTANCE_CLUSTER_NAME = "TEST1";
    
    public static void main(String[] args) throws NacosException, InterruptedException {
        
        Properties properties = new Properties();
        properties.setProperty("serverAddr", System.getProperty("serverAddr", "localhost"));
        properties.setProperty("namespace", System.getProperty("namespace", "public"));
        
        NamingService naming = NamingFactory.createNamingService(properties);
        
        naming.registerInstance(INSTANCE_SERVICE_NAME, INSTANCE_IP, INSTANCE_PORT, INSTANCE_CLUSTER_NAME);
        
        System.out.println("[instances after register] " + naming.getAllInstances(INSTANCE_SERVICE_NAME));
        
        Executor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("test-thread");
                    return thread;
                });
        
        naming.subscribe(INSTANCE_SERVICE_NAME, new AbstractEventListener() {
            
            //EventListener onEvent is sync to handle, If process too low in onEvent, maybe block other onEvent callback.
            //So you can override getExecutor() to async handle event.
            @Override
            public Executor getExecutor() {
                return executor;
            }
            
            @Override
            public void onEvent(Event event) {
                System.out.println("[serviceName] " + ((NamingEvent) event).getServiceName());
                System.out.println("[instances from event] " + ((NamingEvent) event).getInstances());
            }
        });
        
        naming.deregisterInstance(INSTANCE_SERVICE_NAME, INSTANCE_IP, INSTANCE_PORT, INSTANCE_CLUSTER_NAME);
        
        Thread.sleep(1000);
        
        System.out.println("[instances after deregister] " + naming.getAllInstances(INSTANCE_SERVICE_NAME));
        
        Thread.sleep(1000);
    }
}
