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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.TEST_PORT;
import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;
import static com.alibaba.nacos.test.naming.NamingBase.verifyInstanceList;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Created by wangtong.wt on 2018/6/20.
 *
 * @author wangtong.wt
 * @date 2018/6/20
 */
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class SubscribeCluster_ITCase {
    
    private NamingService naming;
    
    @LocalServerPort
    private int port;
    
    private volatile List<Instance> instances = Collections.emptyList();
    
    @BeforeEach
    void init() throws Exception {
        instances.clear();
        if (naming == null) {
            //TimeUnit.SECONDS.sleep(10);
            naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        }
    }
    
    /**
     * 添加IP，收到通知
     *
     * @throws Exception
     */
    @Test
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    void subscribeAdd() throws Exception {
        String serviceName = randomDomainName();
        
        naming.subscribe(serviceName, Arrays.asList("c1"), new EventListener() {
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
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    void subscribeDelete() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        
        TimeUnit.SECONDS.sleep(3);
        
        naming.subscribe(serviceName, Arrays.asList("c1"), new EventListener() {
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
    @Timeout(value = 10000L, unit = TimeUnit.MILLISECONDS)
    void subscribeUnhealthy() throws Exception {
        String serviceName = randomDomainName();
        
        naming.subscribe(serviceName, Arrays.asList("c1"), new EventListener() {
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
    
    /**
     * 新增其他cluster IP，不会收到通知
     *
     * @throws Exception
     */
    @Test
    void subscribeOtherCluster() throws Exception {
        String serviceName = randomDomainName();
        
        naming.subscribe(serviceName, Arrays.asList("c2"), new EventListener() {
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
        
        naming.registerInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");
        
        int i = 0;
        while (instances.isEmpty()) {
            Thread.sleep(1000L);
            if (i++ > 10) {
                return;
            }
        }
        
        fail();
    }
}
