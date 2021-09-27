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
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import org.junit.AfterClass;
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
 * Created by wangtong.wt on 2018/6/20.
 *
 * @author wangtong.wt
 * @date 2018/6/20
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class SelectInstances_ITCase {

    private static NamingService naming;
    
    private static NamingService naming1;
    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {
        NamingBase.prepareServer(port);
        if (naming == null) {
            //TimeUnit.SECONDS.sleep(10);
            naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
            naming1 = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        }
        int i = 5;
        while (i >= 0) {
            i --;
            if (!"UP".equals(naming.getServerStatus())) {
                Thread.sleep(1000L);
                continue;
            }
            break;
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
    }

    /**
     * 获取所有健康的Instance
     *
     * @throws Exception
     */
    @Test
    public void selectHealthyInstances() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT);
        naming1.registerInstance(serviceName, "1.1.1.1", 9090);

        TimeUnit.SECONDS.sleep(10);

        List<Instance> instances = naming.selectInstances(serviceName, true);

        Assert.assertEquals(2, instances.size());

        Instance instanceNotH = null;
        List<Instance> instancesGet = naming.getAllInstances(serviceName);
        for (Instance instance : instancesGet) {
            if (!instance.isHealthy()) {
                instanceNotH = instance;
            }
        }

        instancesGet.remove(instanceNotH);

        Assert.assertTrue(verifyInstanceList(instances, instancesGet));
    }

    /**
     * 获取所有不健康的Instance
     *
     * @throws Exception
     */
    @Test
    public void selectUnhealthyInstances() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "1.1.1.1", TEST_PORT);
        naming1.registerInstance(serviceName, "1.1.1.2", TEST_PORT);

        TimeUnit.SECONDS.sleep(8);
        List<Instance> instances = naming.selectInstances(serviceName, false);

        TimeUnit.SECONDS.sleep(2);
        Assert.assertEquals(0, instances.size());

        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        Assert.assertTrue(verifyInstanceList(instances, instancesGet));
    }

    /**
     * 获取指定cluster中（单个、多个）所有健康的Instance
     *
     * @throws Exception
     */
    @Test
    public void selectHealthyInstancesClusters() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "127.0.0.2", 9090, "c2");

        TimeUnit.SECONDS.sleep(8);
        List<Instance> instances = naming.selectInstances(serviceName, Arrays.asList("c1", "c2"), true);
        TimeUnit.SECONDS.sleep(2);
        Assert.assertEquals(instances.size(), 2);

        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        Assert.assertTrue(verifyInstanceList(instances, instancesGet));
    }

    /**
     * 获取指定cluster中（单个、多个）不所有健康的Instance
     *
     * @throws Exception
     */
    @Test
    public void selectUnhealthyInstancesClusters() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "1.1.1.2", TEST_PORT, "c2");

        TimeUnit.SECONDS.sleep(8);
        List<Instance> instances = naming.selectInstances(serviceName, Arrays.asList("c1", "c2"), false);
        TimeUnit.SECONDS.sleep(2);
        Assert.assertEquals(0, instances.size());

        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        Assert.assertTrue(verifyInstanceList(instances, instancesGet));
    }

    @Test
    public void selectInstancesCheckClusterName() throws Exception {

        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "1.1.1.1", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "1.1.1.2", TEST_PORT, "c2");

        TimeUnit.SECONDS.sleep(8);

        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        Assert.assertEquals(2, instancesGet.size());

        for (Instance instance : instancesGet) {
            if (instance.getIp().equals("1.1.1.1")) {
                Assert.assertEquals(instance.getClusterName(), "c1");
            }
            if (instance.getIp().equals("2.2.2.2")) {
                Assert.assertEquals(instance.getClusterName(), "c2");
            }
        }
    }


    /**
     * 获取权重不为0的Instance
     *
     * @throws Exception
     */
    @Test
    public void selectAllWeightedInstances() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT);
        naming1.registerInstance(serviceName, "1.1.1.1", 9090);

        TimeUnit.SECONDS.sleep(10);

        List<Instance> instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(instances.size(), 2);

        instances = naming.selectInstances(serviceName, true);

        Assert.assertEquals(2, instances.size());

        instances.get(0).setWeight(0);

        instances = naming.selectInstances(serviceName, true);

        Assert.assertEquals(1, instances.size());

        Instance instanceNotH = null;
        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        for (Instance instance : instancesGet) {
            if (!instance.isHealthy() || !instance.isEnabled() || instance.getWeight() <= 0) {

                instanceNotH = instance;
            }
        }

        instancesGet.remove(instanceNotH);

        Assert.assertTrue(verifyInstanceList(instances, instancesGet));
    }


    /**
     * 获取指定cluster中（单个、多个）所有权重不为0的Instance
     *
     * @throws Exception
     */
    @Test
    public void selectAllWeightedInstancesClusters() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "1.1.1.1", 9090, "c2");

        TimeUnit.SECONDS.sleep(10);

        List<Instance> instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(instances.size(), 2);

        instances = naming.selectInstances(serviceName, Arrays.asList("c1", "c2"), true);

        Assert.assertEquals(2, instances.size());

        instances.get(0).setWeight(0);

        instances = naming.selectInstances(serviceName, Arrays.asList("c1", "c2"), true);

        Assert.assertEquals(1, instances.size());

        Instance instanceNotH = null;
        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        for (Instance instance : instancesGet) {
            if (!instance.isHealthy() || !instance.isEnabled() || instance.getWeight() <= 0) {

                instanceNotH = instance;
            }
        }

        instancesGet.remove(instanceNotH);

        Assert.assertTrue(verifyInstanceList(instances, instancesGet));
    }


    /**
     * 获取所有Enable的Instance
     *
     * @throws Exception
     */
    @Test
    public void selectAllEnabledInstances() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT);
        naming1.registerInstance(serviceName, "1.1.1.1", 9090);

        TimeUnit.SECONDS.sleep(10);

        List<Instance> instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(instances.size(), 2);

        instances = naming.selectInstances(serviceName, true);

        Assert.assertEquals(2, instances.size());

        instances.get(0).setEnabled(false);

        instances = naming.selectInstances(serviceName, true);

        Assert.assertEquals(1, instances.size());

        Instance instanceNotH = null;
        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        for (Instance instance : instancesGet) {
            if (!instance.isHealthy() || !instance.isEnabled() || instance.getWeight() <= 0) {

                instanceNotH = instance;
            }
        }

        instancesGet.remove(instanceNotH);

        Assert.assertTrue(verifyInstanceList(instances, instancesGet));
    }


    /**
     * 获取指定cluster中（单个、多个）所有Enabled的Instance
     *
     * @throws Exception
     */
    @Test
    public void selectAllEnabledInstancesClusters() throws Exception {
        String serviceName = randomDomainName();
        System.out.println(serviceName);
        naming.registerInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "1.1.1.1", 9090, "c2");

        TimeUnit.SECONDS.sleep(5);

        List<Instance> instances = naming.getAllInstances(serviceName);

        Assert.assertEquals(instances.size(), 2);

        instances = naming.selectInstances(serviceName, Arrays.asList("c1", "c2"), true);

        Assert.assertEquals(2, instances.size());

        instances.get(0).setEnabled(false);

        instances = naming.selectInstances(serviceName, Arrays.asList("c1", "c2"), true);

        TimeUnit.SECONDS.sleep(5);

        Assert.assertEquals(1, instances.size());

        Instance instanceNotH = null;
        List<Instance> instancesGet = naming.getAllInstances(serviceName);

        for (Instance instance : instancesGet) {
            if (!instance.isHealthy() || !instance.isEnabled() || instance.getWeight() <= 0) {

                instanceNotH = instance;
            }
        }

        instancesGet.remove(instanceNotH);

        Assert.assertTrue(verifyInstanceList(instances, instancesGet));
    }

    @Test
    @Ignore("TODO nacos 2.0 can't support selector for now")
    public void getServiceListWithSelector() throws NacosException, InterruptedException {

        String serviceName = randomDomainName();
        Instance instance = new Instance();
        instance.setIp("128.0.0.1");
        instance.setPort(999);
        instance.setServiceName(serviceName);

        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("registerSource", "dubbo");
        instance.setMetadata(metadata);

        naming.registerInstance(serviceName, instance);
        naming.registerInstance(serviceName, "127.0.0.1", 80, "c1");
        naming.registerInstance(serviceName, "127.0.0.2", 80, "c2");

        TimeUnit.SECONDS.sleep(10);

        ExpressionSelector expressionSelector = new ExpressionSelector();
        expressionSelector.setExpression("INSTANCE.label.registerSource = 'dubbo'");
        ListView<String> serviceList = naming.getServicesOfServer(1, 10, expressionSelector);

        Assert.assertTrue(serviceList.getData().contains(serviceName));

        serviceName = randomDomainName();

        instance.setServiceName(serviceName);
        metadata.put("registerSource", "spring");
        instance.setMetadata(metadata);

        naming.registerInstance(serviceName, instance);

        TimeUnit.SECONDS.sleep(10);

        expressionSelector.setExpression("INSTANCE.label.registerSource = 'spring'");
        serviceList = naming.getServicesOfServer(1, 10, expressionSelector);

        Assert.assertTrue(serviceList.getData().contains(serviceName));

    }


}
