package com.alibaba.nacos.example;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.AbstractEventListener;
import com.alibaba.nacos.api.naming.listener.AbstractFuzzyWatchEventListener;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.FuzzyWatchNotifyEvent;
import com.alibaba.nacos.api.naming.listener.NamingEvent;

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
public class FuzzyWatchExample {
    
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
        
        naming.fuzzyWatch(DEFAULT_GROUP, new AbstractFuzzyWatchEventListener() {
            
            //EventListener onEvent is sync to handle, If process too low in onEvent, maybe block other onEvent callback.
            //So you can override getExecutor() to async handle event.
            @Override
            public Executor getExecutor() {
                return executor;
            }
            
            @Override
            public void onEvent(FuzzyWatchNotifyEvent event) {
                System.out.println("[Fuzzy-Watch-GROUP]changed service name: " + event.getService().getGroupedServiceName());
                System.out.println("[Fuzzy-Watch-GROUP]change type: " + event.getChangeType());
            }
        });
        
        naming.fuzzyWatch("nacos.test", DEFAULT_GROUP, new AbstractFuzzyWatchEventListener() {
            
            @Override
            public Executor getExecutor() {
                return executor;
            }
            
            @Override
            public void onEvent(FuzzyWatchNotifyEvent event) {
                System.out.println("[Prefix-Fuzzy-Watch]changed service name: " + event.getService().getGroupedServiceName());
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
