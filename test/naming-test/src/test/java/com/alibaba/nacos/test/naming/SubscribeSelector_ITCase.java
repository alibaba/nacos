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

package com.alibaba.nacos.test.naming;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.client.naming.selector.DefaultNamingSelector;
import com.alibaba.nacos.client.naming.selector.NamingSelectorFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author lideyou
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class SubscribeSelector_ITCase extends NamingBase {
    
    private NamingService naming;
    
    private NamingSelector selector = new DefaultNamingSelector(instance -> instance.getIp().startsWith("172.18.137"));
    
    @LocalServerPort
    private int port;
    
    @Before
    public void init() throws Exception {
        instances.clear();
        if (naming == null) {
            naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        }
    }
    
    private volatile List<Instance> instances = Collections.emptyList();
    
    /**
     * Add IP and receive notification.
     *
     * @throws Exception
     */
    @Test(timeout = 10000L)
    public void subscribeAdd() throws Exception {
        String serviceName = randomDomainName();
        
        naming.subscribe(serviceName, selector, new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
            }
        });
        
        naming.registerInstance(serviceName, "172.18.137.1", TEST_PORT);
        
        while (instances.isEmpty()) {
            Thread.sleep(1000L);
        }
        
        Assert.assertTrue(verifyInstanceList(instances, naming.getAllInstances(serviceName)));
    }
    
    /**
     * Delete IP and receive notification.
     *
     * @throws Exception
     */
    @Test(timeout = 10000L)
    public void subscribeDelete() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "172.18.137.1", TEST_PORT, "c1");
        
        TimeUnit.SECONDS.sleep(3);
        
        naming.subscribe(serviceName, selector, new EventListener() {
            int index = 0;
            
            @Override
            public void onEvent(Event event) {
                instances = ((NamingEvent) event).getInstances();
                if (index == 0) {
                    index++;
                    return;
                }
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
            }
        });
        
        TimeUnit.SECONDS.sleep(1);
        
        naming.deregisterInstance(serviceName, "172.18.137.1", TEST_PORT, "c1");
        
        while (!instances.isEmpty()) {
            Thread.sleep(1000L);
        }
        
        Assert.assertTrue(instances.isEmpty());
    }
    
    /**
     * Add non target IP and do not receive notification.
     *
     * @throws Exception
     */
    @Test
    public void subscribeOtherIp() throws Exception {
        String serviceName = randomDomainName();
        
        naming.subscribe(serviceName, selector, new EventListener() {
            int index = 0;
            
            @Override
            public void onEvent(Event event) {
                instances = ((NamingEvent) event).getInstances();
                if (index == 0) {
                    index++;
                    return;
                }
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
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
        
        Assert.fail();
    }
}
