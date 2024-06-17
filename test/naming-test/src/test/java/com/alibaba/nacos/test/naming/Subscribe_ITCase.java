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

package com.alibaba.nacos.test.naming;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.listener.AbstractNamingChangeListener;
import com.alibaba.nacos.client.naming.listener.NamingChangeEvent;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.base.Params;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Created by wangtong.wt on 2018/6/20.
 *
 * @author wangtong.wt
 * @date 2018/6/20
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class Subscribe_ITCase extends NamingBase {
    
    private NamingService naming;
    
    @LocalServerPort
    private int port;
    
    private volatile List<Instance> instances = Collections.emptyList();
    
    @BeforeEach
    void init() throws Exception {
        instances.clear();
        if (naming == null) {
            //TimeUnit.SECONDS.sleep(10);
            Properties properties = new Properties();
            properties.setProperty("namingRequestTimeout", "300000");
            properties.setProperty("serverAddr", "127.0.0.1" + ":" + port);
            naming = NamingFactory.createNamingService(properties);
        }
        String url = String.format("http://localhost:%d/", port);
        this.base = new URL(url);
    }
    
    /**
     * 添加IP，收到通知
     *
     * @throws Exception
     */
    @Test
    @Timeout(value = 4 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    void subscribeAdd() throws Exception {
        String serviceName = randomDomainName();
        
        naming.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
            }
        });
        
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        
        while (instances.isEmpty()) {
            Thread.sleep(1000L);
        }
        
        assertTrue(verifyInstanceList(instances, naming.getAllInstances(serviceName)));
    }
    
    /**
     * 删除IP，收到通知
     *
     * @throws Exception
     */
    @Test
    @Timeout(value = 4 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    void subscribeDelete() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        
        TimeUnit.SECONDS.sleep(3);
        
        naming.subscribe(serviceName, new EventListener() {
            int index = 0;
            
            @Override
            public void onEvent(Event event) {
                if (index == 0) {
                    index++;
                    return;
                }
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
            }
        });
        
        TimeUnit.SECONDS.sleep(1);
        
        naming.deregisterInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        
        while (!instances.isEmpty()) {
            Thread.sleep(1000L);
        }
        
        assertTrue(instances.isEmpty());
    }
    
    /**
     * 添加不可用IP，收到通知
     *
     * @throws Exception
     */
    @Test
    @Timeout(value = 4 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    void subscribeUnhealthy() throws Exception {
        String serviceName = randomDomainName();
        
        naming.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
            }
        });
        
        naming.registerInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");
        
        while (instances.isEmpty()) {
            Thread.sleep(1000L);
        }
        
        assertTrue(verifyInstanceList(instances, naming.getAllInstances(serviceName)));
    }
    
    @Test
    @Timeout(value = 4 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    void subscribeEmpty() throws Exception {
        
        String serviceName = randomDomainName();
        
        naming.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
            }
        });
        
        naming.registerInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");
        
        while (instances.isEmpty()) {
            Thread.sleep(1000L);
        }
        
        assertTrue(verifyInstanceList(instances, naming.getAllInstances(serviceName)));
        
        naming.deregisterInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");
        
        while (!instances.isEmpty()) {
            Thread.sleep(1000L);
        }
        
        assertEquals(0, instances.size());
        assertEquals(0, naming.getAllInstances(serviceName).size());
    }
    
    @Test
    void querySubscribers() throws Exception {
        
        String serviceName = randomDomainName();
        
        naming.registerInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");
        
        EventListener listener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
            }
        };
        
        naming.subscribe(serviceName, listener);
        
        TimeUnit.SECONDS.sleep(3);
        
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service/subscribers",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("pageNo", "1").appendParam("pageSize", "10").done(),
                String.class, HttpMethod.GET);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        JsonNode body = JacksonUtils.toObj(response.getBody());
        
        assertEquals(1, body.get("subscribers").size());
        
        Properties properties = new Properties();
        properties.setProperty("namingRequestTimeout", "300000");
        properties.setProperty("serverAddr", "127.0.0.1" + ":" + port);
        NamingService naming2 = NamingFactory.createNamingService(properties);
        
        naming2.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
            }
        });
        
        TimeUnit.SECONDS.sleep(3);
        
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service/subscribers",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("pageNo", "1").appendParam("pageSize", "10").done(),
                String.class, HttpMethod.GET);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        body = JacksonUtils.toObj(response.getBody());
        
        // server will remove duplicate subscriber by ip port service app and so on
        assertEquals(1, body.get("subscribers").size());
    }
    
    @Test
    void subscribeSameServiceForTwoNamingService() throws Exception {
        Properties properties1 = new Properties();
        properties1.setProperty("serverAddr", "127.0.0.1" + ":" + port);
        properties1.setProperty("namespace", "ns-001");
        final NamingService naming1 = NamingFactory.createNamingService(properties1);
        Properties properties2 = new Properties();
        properties2.setProperty("serverAddr", "127.0.0.1" + ":" + port);
        properties2.setProperty("namespace", "ns-002");
        final NamingService naming2 = NamingFactory.createNamingService(properties2);
        
        final ConcurrentHashSet<Instance> concurrentHashSet1 = new ConcurrentHashSet();
        final String serviceName = randomDomainName();
        
        naming1.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println("Event from naming1: " + ((NamingEvent) event).getServiceName());
                System.out.println("Event from naming1: " + ((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
            }
        });
        naming2.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println("Event from naming2: " + ((NamingEvent) event).getServiceName());
                System.out.println("Event from naming2: " + ((NamingEvent) event).getInstances());
                concurrentHashSet1.addAll(((NamingEvent) event).getInstances());
            }
        });
        
        naming1.registerInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");
        
        while (instances.isEmpty()) {
            Thread.sleep(1000L);
        }
        
        try {
            assertTrue(verifyInstanceList(instances, naming1.getAllInstances(serviceName)));
            assertEquals(0, concurrentHashSet1.size());
        } finally {
            naming1.shutDown();
            naming2.shutDown();
        }
    }
    
    @Test
    void subscribeUsingAbstractNamingChangeListener() throws Exception {
        String serviceName = randomDomainName();
        
        naming.subscribe(serviceName, new AbstractNamingChangeListener() {
            @Override
            public void onChange(NamingChangeEvent event) {
                System.out.println(event.getServiceName());
                System.out.println(event.getInstances());
                instances = event.getInstances();
                assertTrue(event.isAdded());
            }
        });
        
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        
        while (instances.isEmpty()) {
            Thread.sleep(1000L);
        }
        
        assertTrue(verifyInstanceList(instances, naming.getAllInstances(serviceName)));
    }
    
    @Test
    void testListenerFirstCallback() throws Exception {
        String serviceName = randomDomainName();
        AtomicInteger count = new AtomicInteger(0);
        naming.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
                count.incrementAndGet();
            }
        });
        
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        
        while (instances.isEmpty()) {
            Thread.sleep(1000L);
        }
        
        naming.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
                count.incrementAndGet();
            }
        });
        
        int i = 0;
        while (count.get() < 2) {
            Thread.sleep(1000L);
            if (i++ > 10) {
                fail();
            }
        }
    }
}
