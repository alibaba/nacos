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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.NacosNamingService;
import com.alibaba.nacos.naming.NamingApp;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.*;

/**
 * Created by lingwei.cao on 2018/11/13.
 *
 * @author lingwei.cao
 * @date 2018/11/13
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AutoDeregisterInstance_ITCase {

    private NamingService naming;
    private NamingService naming2;
    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {
        if (naming == null) {
            TimeUnit.SECONDS.sleep(10);
            naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
//            naming = NamingFactory.createNamingService("11.239.112.230:8848,11.239.113.118:8848,11.239.113.156:8848");

        }
    }




    /**
     * 客户端停止上报实例心跳，服务端自动注销实例
     *
     * @throws Exception
     */
    @Test
    public void autoDregDomTest() throws Exception {

        String serviceName = randomDomainName();
//        String serviceName="test.1";
        System.out.println(serviceName);

        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT,"c1");
        naming.registerInstance(serviceName, "127.0.0.2", TEST_PORT,"c2");

        TimeUnit.SECONDS.sleep(5);

        List<Instance> instances;
        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(2, instances.size());

        NacosNamingService namingServiceImpl = (NacosNamingService) naming;

        namingServiceImpl.getBeatReactor().removeBeatInfo(serviceName, "127.0.0.1", TEST_PORT);

        TimeUnit.SECONDS.sleep(35);

        instances = naming.getAllInstances(serviceName);

//        TimeUnit.SECONDS.sleep(1000000L);

        Assert.assertEquals(1, instances.size());

        instances = naming.getAllInstances(serviceName, Arrays.asList("c2"));
        Assert.assertEquals(instances.size(), 1);

        instances = naming.getAllInstances(serviceName, Arrays.asList("c1"));
        Assert.assertEquals(0, instances.size());

//        TimeUnit.SECONDS.sleep(1000000L);

    }


    /**
     * 客户端停止上报实例心跳，服务端自动注销实例,恢复心跳，服务端自动注册实例
     *
     * @throws Exception
     */
    @Test
    public void autoRegDomTest() throws Exception {

        String serviceName = randomDomainName();

        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT);
        naming.registerInstance(serviceName, "127.0.0.2", TEST_PORT);

        TimeUnit.SECONDS.sleep(5);

        List<Instance> instances;
        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(instances.size(), 2);

        NacosNamingService namingServiceImpl = (NacosNamingService) naming;

        namingServiceImpl.getBeatReactor().removeBeatInfo(serviceName, "127.0.0.1", TEST_PORT);

        TimeUnit.SECONDS.sleep(35);

      //  namingServiceImpl.getBeatReactor().



    }




}
