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
import com.alibaba.nacos.api.naming.pojo.Instance;
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

import static com.alibaba.nacos.test.naming.NamingBase.TEST_PORT;
import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;

/**
 * Created by wangtong.wt on 2018/6/20.
 *
 * @author wangtong.wt
 * @date 2018/6/20
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DeregisterInstance_ITCase {

    private NamingService naming;
    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {

        NamingBase.prepareServer(port);

        if (naming == null) {
            //TimeUnit.SECONDS.sleep(10);
            naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        }

        while (true) {
            if (!"UP".equals(naming.getServerStatus())) {
                Thread.sleep(1000L);
                continue;
            }
            break;
        }
    }

    /**
     * 删除service中默认cluster的一个ip
     *
     * @throws Exception
     */
    @Test
    public void dregDomTest() throws Exception {
        String serviceName = randomDomainName();
        System.out.println(serviceName);
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT);
        naming.registerInstance(serviceName, "127.0.0.2", TEST_PORT);

        List<Instance> instances = naming.getAllInstances(serviceName);
        verifyInstanceList(instances, 2, serviceName);

        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(2, instances.size());

        naming.deregisterInstance(serviceName, "127.0.0.1", TEST_PORT);

        TimeUnit.SECONDS.sleep(2);

        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(instances.size(), 1);
        Assert.assertEquals(instances.get(0).getIp(), "127.0.0.2");

        naming.deregisterInstance(serviceName, "127.0.0.2", TEST_PORT);

        TimeUnit.SECONDS.sleep(3);

        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(0, instances.size());
    }

    /**
     * 删除service中指定cluster的一个ip
     *
     * @throws Exception
     */
    @Test
    public void dregDomClusterTest() throws Exception {

        String serviceName = randomDomainName();
        System.out.println(serviceName);

        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        naming.registerInstance(serviceName, "127.0.0.2", TEST_PORT, "c2");

        List<Instance> instances;
        instances = naming.getAllInstances(serviceName);
        verifyInstanceList(instances, 2, serviceName);

        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(instances.size(), 2);

        naming.deregisterInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");

        TimeUnit.SECONDS.sleep(5);

        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(1, instances.size());

        instances = naming.getAllInstances(serviceName, Arrays.asList("c2"));
        Assert.assertEquals(instances.size(), 1);

        instances = naming.getAllInstances(serviceName, Arrays.asList("c1"));
    }




    /**
     * 删除service中最后一个Instance，允许删除，结果返回null
     *
     * @throws Exception
     */
    @Test
    public void dregLastDomTest() throws Exception {

        String serviceName = randomDomainName();

        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        naming.registerInstance(serviceName, "127.0.0.2", TEST_PORT, "c2");

        List<Instance> instances;
        instances = naming.getAllInstances(serviceName);
        verifyInstanceList(instances, 2, serviceName);

        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(2, instances.size());

        naming.deregisterInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");

        TimeUnit.SECONDS.sleep(2);

        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(1, instances.size());

        instances = naming.getAllInstances(serviceName, Arrays.asList("c2"));
        Assert.assertEquals(1, instances.size());

        naming.deregisterInstance(serviceName,"127.0.0.2", TEST_PORT, "c2");
        TimeUnit.SECONDS.sleep(5);
        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(0, instances.size());
    }

    public void verifyInstanceList(List<Instance> instances, int size, String serviceName) throws Exception {
        int i = 0;
        while ( i < 20 ) {
            instances = naming.getAllInstances(serviceName);
            if (instances.size() == size) {
                break;
            } else {
                TimeUnit.SECONDS.sleep(3);
                i++;
            }
        }
    }



}
