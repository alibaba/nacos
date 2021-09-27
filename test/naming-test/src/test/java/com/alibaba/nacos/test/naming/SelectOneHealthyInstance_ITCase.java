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
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
public class SelectOneHealthyInstance_ITCase {

    private static NamingService naming;
    private static NamingService naming1;
    private static NamingService naming2;
    private static NamingService naming3;
    private static NamingService naming4;
    @LocalServerPort
    private int port;
    @Before
    public void init() throws Exception{
        if (naming == null) {
            //TimeUnit.SECONDS.sleep(10);
            naming = NamingFactory.createNamingService("127.0.0.1"+":"+port);
            naming1 = NamingFactory.createNamingService("127.0.0.1"+":"+port);
            naming2 = NamingFactory.createNamingService("127.0.0.1"+":"+port);
            naming3 = NamingFactory.createNamingService("127.0.0.1"+":"+port);
            naming4 = NamingFactory.createNamingService("127.0.0.1"+":"+port);
        }
    }
    
    @AfterClass
    public static void tearDown() throws NacosException {
        if (null != naming) {
            naming.shutDown();
        }
        if (null != naming1) {
            naming1.shutDown();
        }
        if (null != naming2) {
            naming2.shutDown();
        }
        if (null != naming3) {
            naming3.shutDown();
        }
        if (null != naming4) {
            naming4.shutDown();
        }
    }

    /**
     * 获取一个健康的Instance
     * @throws Exception
     */
    @Test
    public void selectOneHealthyInstances() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT);
        naming1.registerInstance(serviceName, "127.0.0.1", 60000);

        TimeUnit.SECONDS.sleep(2);
        Instance instance = naming.selectOneHealthyInstance(serviceName);

        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        for (Instance instance1 : instancesGet) {
            if (instance1.getIp().equals(instance.getIp())&&
                instance1.getPort() == instance.getPort()) {
                Assert.assertTrue(instance.isHealthy());
                Assert.assertTrue(verifyInstance(instance1, instance));
                return;
            }
        }

        Assert.fail();
    }

    /**
     * 获取指定单个cluster中一个健康的Instance
     * @throws Exception
     */
    @Test
    public void selectOneHealthyInstancesCluster() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "127.0.0.1", 60000, "c1");
        naming2.registerInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");
        naming3.registerInstance(serviceName, "127.0.0.1", 60001, "c1");
        naming4.registerInstance(serviceName, "127.0.0.1", 60002, "c2");

        TimeUnit.SECONDS.sleep(2);
        Instance instance = naming.selectOneHealthyInstance(serviceName, Arrays.asList("c1"));

        Assert.assertNotSame("1.1.1.1", instance.getIp());
        Assert.assertTrue(instance.getPort() != 60002);

        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        for (Instance instance1 : instancesGet) {
            if (instance1.getIp().equals(instance.getIp())&&
                instance1.getPort() == instance.getPort()) {
                Assert.assertTrue(instance.isHealthy());
                Assert.assertTrue(verifyInstance(instance1, instance));
                return;
            }
        }

        Assert.fail();
    }

    /**
     * 获取指定多个cluster中一个健康的Instance
     * @throws Exception
     */
    @Test
    public void selectOneHealthyInstancesClusters() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        naming2.registerInstance(serviceName, "127.0.0.1", 60000, "c1");
        naming3.registerInstance(serviceName, "127.0.0.1", 60001, "c2");

        TimeUnit.SECONDS.sleep(2);
        Instance instance = naming.selectOneHealthyInstance(serviceName, Arrays.asList("c1", "c2"));
        Assert.assertNotSame("1.1.1.1", instance.getIp());

        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        for (Instance instance1 : instancesGet) {
            if (instance1.getIp().equals(instance.getIp()) &&
                instance1.getPort() == instance.getPort()) {
                Assert.assertTrue(instance.isHealthy());
                Assert.assertTrue(verifyInstance(instance1, instance));
                return;
            }
        }

        Assert.fail();
    }
}
