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

import java.util.Properties;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;

/**
 * Nacos naming example.
 *
 * @author nkorange
 */
public class NamingExample {

    public static void main(String[] args) throws NacosException, InterruptedException {

        Properties properties = new Properties();
        properties.setProperty("serverAddr", "192.168.50.39:8848");
//        properties.setProperty("namespace", "3c758c4d-43de-41bb-b75a-920471a52d76");

        NamingService naming = NamingFactory.createNamingService(properties);

        naming.registerInstance("userProvider", "11.11.11.111", 8887, "TEST1");

        naming.registerInstance("userProvider", "11.11.11.111", 8888, "TEST1");

        naming.registerInstance("videoProvider", "11.11.11.111", 8889, "TEST1");

        naming.registerInstance("videoProvider", "2.2.2.21", 9999, "DEFAULT");

        naming.registerInstance("videoProvider", "5.5.5.5", 9999, "DEFAULT");

        System.out.println("1----------------" + naming.getAllInstances("userProvider"));

        naming.deregisterInstance("videoProvider", "5.5.5.5", 9999, "DEFAULT");

        System.out.println("2----------------" + naming.getAllInstances("videoProvider"));

        naming.subscribe("videoProvider", new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
            }
        });


        Thread.sleep(Integer.MAX_VALUE);
    }
}
