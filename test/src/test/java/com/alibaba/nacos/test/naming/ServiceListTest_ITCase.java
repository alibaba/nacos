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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.test.naming.NamingBase.TEST_PORT;
import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;

/**
 * @author nkorange
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ServiceListTest_ITCase {

    private NamingService naming;

    private volatile List<Instance> instances = Collections.emptyList();

    private static int listenseCount = 0;

    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {
        if (naming == null) {
            naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        }
    }

    @Test
    public void serviceList() throws NacosException {
        naming.getServicesOfServer(1, 10);
    }

    /**
     * @throws NacosException
     * @description 获取当前订阅的所有服务
     */
    @Test
    public void getSubscribeServices() throws NacosException, InterruptedException {

        ListView<String> listView = naming.getServicesOfServer(1, 10);
        if (listView != null && listView.getCount() > 0) {
            naming.getAllInstances(listView.getData().get(0));
        }
        List<ServiceInfo> serviceInfoList = naming.getSubscribeServices();
        int count = serviceInfoList.size();

        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");

        naming.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {

            }
        });

        serviceInfoList = naming.getSubscribeServices();
        Assert.assertEquals(count + 1, serviceInfoList.size());
    }

    /**
     * @throws NacosException
     * @description 删除注册，获取当前订阅的所有服务
     */
    @Test
    public void getSubscribeServices_deregisterInstance() throws NacosException, InterruptedException {
        listenseCount = 0;
        EventListener listener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                listenseCount++;
            }
        };

        List<ServiceInfo> serviceInfoList = naming.getSubscribeServices();
        int count = serviceInfoList.size();

        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");

        naming.subscribe(serviceName, listener);

        serviceInfoList = naming.getSubscribeServices();

        Assert.assertEquals(count + 1, serviceInfoList.size());

        naming.deregisterInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");

        Assert.assertEquals(count + 1, serviceInfoList.size());
    }
}
