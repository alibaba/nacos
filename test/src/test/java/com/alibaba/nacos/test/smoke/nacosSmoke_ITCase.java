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

package com.alibaba.nacos.test.smoke;

import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class nacosSmoke_ITCase {

    private static Logger logger = Logger.getLogger(nacosSmoke_ITCase.class);

    @Before
    public void setUp() {
        logger.info(String.format("nacosSmoke_ITCase: %s;", "setUp"));
    }

    @After
    public void tearDown() {
        logger.info(String.format("nacosSmoke_ITCase: %s;", "tearDown"));
    }

    @Test
    public void testSmoke() throws Exception {
        NamingService namingService = NamingFactory.createNamingService("127.0.0.1:8848");
        namingService.subscribe("test.1", new EventListener() {
            @Override
            public void onEvent(Event event) {
                NamingEvent ne = (NamingEvent)event;
                System.out.println(ne.getServiceName() + ": " + ne.getInstances());
            }
        });

        namingService.registerInstance("test.1", "1.1.1.1", 80);
        TimeUnit.SECONDS.sleep(5L);
        namingService.registerInstance("test.1", "2.2.2.2", 80);
        TimeUnit.SECONDS.sleep(5L);
        //namingService.deregisterInstance("test.1", "1.1.1.1", 80);
        //TimeUnit.SECONDS.sleep(5L);
        //namingService.deregisterInstance("test.1", "2.2.2.2", 80);

        TimeUnit.SECONDS.sleep(100000000L);
    }


    @Test
    public void myTest() {
    }



}
