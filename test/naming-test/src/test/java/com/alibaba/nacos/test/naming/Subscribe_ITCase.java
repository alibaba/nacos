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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.base.Params;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Created by wangtong.wt on 2018/6/20.
 *
 * @author wangtong.wt
 * @date 2018/6/20
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class Subscribe_ITCase extends NamingBase {

    private NamingService naming;
    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {
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

    private volatile List<Instance> instances = Collections.emptyList();

    /**
     * 添加IP，收到通知
     *
     * @throws Exception
     */
    @Test(timeout = 4 * TIME_OUT)
    public void subscribeAdd() throws Exception {
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

        Assert.assertTrue(verifyInstanceList(instances, naming.getAllInstances(serviceName)));
    }

    /**
     * 删除IP，收到通知
     *
     * @throws Exception
     */
    @Test(timeout = 4 * TIME_OUT)
    public void subscribeDelete() throws Exception {
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

        Assert.assertTrue(instances.isEmpty());
    }

    /**
     * 添加不可用IP，收到通知
     *
     * @throws Exception
     */
    @Test(timeout = 4 * TIME_OUT)
    public void subscribeUnhealthy() throws Exception {
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

        Assert.assertTrue(verifyInstanceList(instances, naming.getAllInstances(serviceName)));
    }
    
    @Test(timeout = 4 * TIME_OUT)
    public void subscribeEmpty() throws Exception {

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

        Assert.assertTrue(verifyInstanceList(instances, naming.getAllInstances(serviceName)));

        naming.deregisterInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");

        while (!instances.isEmpty()) {
            Thread.sleep(1000L);
        }

        Assert.assertEquals(0, instances.size());
        Assert.assertEquals(0, naming.getAllInstances(serviceName).size());
    }

    @Test
    public void querySubscribers() throws Exception {

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
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("pageNo", "1")
                .appendParam("pageSize", "10")
                .done(),
            String.class,
            HttpMethod.GET);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JsonNode body = JacksonUtils.toObj(response.getBody());

        Assert.assertEquals(1, body.get("subscribers").size());
    
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
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("pageNo", "1")
                .appendParam("pageSize", "10")
                .done(),
            String.class,
            HttpMethod.GET);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        body = JacksonUtils.toObj(response.getBody());

        // server will remove duplicate subscriber by ip port service app and so on
        Assert.assertEquals(1, body.get("subscribers").size());
    }

}
