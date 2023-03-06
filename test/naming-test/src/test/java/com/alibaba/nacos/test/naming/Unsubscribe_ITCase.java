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
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.test.naming.NamingBase.*;

/**
 * Created by wangtong.wt on 2018/6/20.
 *
 * @author wangtong.wt
 * @date 2018/6/20
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class Unsubscribe_ITCase {

    private NamingService naming;
    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {
        instances = Collections.emptyList();
        if (naming == null) {
            //TimeUnit.SECONDS.sleep(10);
            naming = NamingFactory.createNamingService("127.0.0.1"+":"+port);
        }
    }

    private volatile List<Instance> instances = Collections.emptyList();

    /**
     * 取消订阅，添加IP，不会收到通知
     * @throws Exception
     */
    @Test
    public void unsubscribe() throws Exception {
        String serviceName = randomDomainName();

        EventListener listener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent)event).getServiceName());
                System.out.println(((NamingEvent)event).getInstances());
                instances = ((NamingEvent)event).getInstances();
            }
        };

        naming.subscribe(serviceName, listener);

        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");

        while (instances.isEmpty()) {
            Thread.sleep(1000L);
        }

        Assert.assertTrue(verifyInstanceList(instances, naming.getAllInstances(serviceName)));

        naming.unsubscribe(serviceName, listener);

        instances = Collections.emptyList();
        naming.registerInstance(serviceName, "127.0.0.2", TEST_PORT, "c1");

        int i = 0;
        while (instances.isEmpty()) {
            Thread.sleep(1000L);
            if (i++ > 10) {
                return;
            }
        }

        Assert.fail();
    }

    /**
     * 取消订阅，在指定cluster添加IP，不会收到通知
     * @throws Exception
     */
    @Test
    public void unsubscribeCluster() throws Exception {
        String serviceName = randomDomainName();

        EventListener listener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent)event).getServiceName());
                System.out.println(((NamingEvent)event).getInstances());
                instances = ((NamingEvent)event).getInstances();
            }
        };

        naming.subscribe(serviceName, Arrays.asList("c1"), listener);

        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");

        while (instances.isEmpty()) {
            Thread.sleep(1000L);
        }

        Assert.assertTrue(verifyInstanceList(instances, naming.getAllInstances(serviceName)));

        naming.unsubscribe(serviceName, Arrays.asList("c1"), listener);

        instances = Collections.emptyList();
        naming.registerInstance(serviceName, "127.0.0.2", TEST_PORT, "c1");

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
