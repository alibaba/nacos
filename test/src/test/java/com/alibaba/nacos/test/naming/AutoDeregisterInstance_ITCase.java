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

import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.NacosNamingService;
import com.alibaba.nacos.client.naming.beat.BeatInfo;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.TEST_PORT;
import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;

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
            //TimeUnit.SECONDS.sleep(10);
            naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        }
    }

    /**
     * 指定cluster中（单个、多个）实例，客户端停止上报实例心跳，服务端自动注销实例
     *
     * @throws Exception
     */
    @Test
    @Ignore
    public void autoDregDomClustersTest() throws Exception {
        String serviceName = randomDomainName();

        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        naming.registerInstance(serviceName, "127.0.0.2", TEST_PORT, "c2");

        TimeUnit.SECONDS.sleep(5);

        List<Instance> instances;
        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(2, instances.size());

        NacosNamingService namingServiceImpl = (NacosNamingService) naming;

        namingServiceImpl.getBeatReactor().removeBeatInfo(serviceName, "127.0.0.1", TEST_PORT);

        //TimeUnit.SECONDS.sleep(40);
        verifyInstanceList(instances, 1, serviceName);
        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(1, instances.size());

        instances = naming.getAllInstances(serviceName, Arrays.asList("c2"));
        Assert.assertEquals(instances.size(), 1);

        instances = naming.getAllInstances(serviceName, Arrays.asList("c1"));
        Assert.assertEquals(0, instances.size());
    }


    /**
     * 客户端停止上报实例心跳，服务端自动注销实例
     *
     * @throws Exception
     */
    @Test
    @Ignore
    public void autoDregDomTest() throws Exception {
        String serviceName = randomDomainName();

        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT);
        naming.registerInstance(serviceName, "127.0.0.2", TEST_PORT);

        TimeUnit.SECONDS.sleep(5);

        List<Instance> instances;
        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(2, instances.size());

        NacosNamingService namingServiceImpl = (NacosNamingService) naming;

        namingServiceImpl.getBeatReactor().removeBeatInfo(serviceName, "127.0.0.1", TEST_PORT);

        //TimeUnit.SECONDS.sleep(40);
        verifyInstanceList(instances, 1, serviceName);
        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(1, instances.size());


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

        //TimeUnit.SECONDS.sleep(40);
        verifyInstanceList(instances, 1, serviceName);

        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(instances.size(), 1);
        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setDom(serviceName);
        beatInfo.setIp("127.0.0.1");
        beatInfo.setPort(TEST_PORT);

        namingServiceImpl.getBeatReactor().addBeatInfo(serviceName, beatInfo);
        verifyInstanceList(instances, 2, serviceName);

        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(instances.size(), 2);
    }


    /**
     * 指定cluster中（单个、多个）实例,客户端停止上报实例心跳，服务端自动注销实例,恢复心跳，服务端自动注册实例
     *
     * @throws Exception
     */
    @Test
    public void autoRegDomClustersTest() throws Exception {

        String serviceName = randomDomainName();

        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        naming.registerInstance(serviceName, "127.0.0.2", TEST_PORT, "c2");

        TimeUnit.SECONDS.sleep(5);

        List<Instance> instances;
        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(2, instances.size());

        NacosNamingService namingServiceImpl = (NacosNamingService) naming;

        namingServiceImpl.getBeatReactor().removeBeatInfo(serviceName, "127.0.0.1", TEST_PORT);

        //TimeUnit.SECONDS.sleep(40);
        verifyInstanceList(instances, 1, serviceName);

        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(instances.size(), 1);
        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setDom(serviceName);
        beatInfo.setIp("127.0.0.1");
        beatInfo.setPort(TEST_PORT);
        beatInfo.setCluster("c1");


        namingServiceImpl.getBeatReactor().addBeatInfo(serviceName, beatInfo);
        //TimeUnit.SECONDS.sleep(15);
        verifyInstanceList(instances, 2, serviceName);

        instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(instances.size(), 2);

        instances = naming.getAllInstances(serviceName, Arrays.asList("c2"));
        Assert.assertEquals(1, instances.size());

        instances = naming.getAllInstances(serviceName, Arrays.asList("c1"));
        Assert.assertEquals(1, instances.size());


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
