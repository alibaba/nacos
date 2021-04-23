/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.LocalServerPort;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.TEST_PORT;
import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;

public abstract class AbstractInstanceOperate_ITCase {
    
    private NamingService naming;
    
    @LocalServerPort
    private int port;
    
    @Value("${server.servlet.context-path}")
    private String contextPath;
    
    @Before
    public void init() throws Exception {
        
        NamingBase.prepareServer(port, contextPath);
        
        if (naming == null) {
            Properties properties = new Properties();
            properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
            properties.put(PropertyKeyConst.CONTEXT_PATH, contextPath);
            //TimeUnit.SECONDS.sleep(10);
            naming = NamingFactory.createNamingService(properties);
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
        
        List<Instance> instances = naming.getAllInstances(serviceName);
        verifyInstanceList(instances, 1, serviceName);
        
        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(1, instances.size());
        
        naming.deregisterInstance(serviceName, "127.0.0.1", TEST_PORT);
        
        TimeUnit.SECONDS.sleep(2);
        
        instances = naming.getAllInstances(serviceName);
        
        Assert.assertEquals(instances.size(), 0);
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
        
        List<Instance> instances;
        instances = naming.getAllInstances(serviceName);
        verifyInstanceList(instances, 1, serviceName);
        
        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(1, instances.size());
        
        naming.deregisterInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        
        TimeUnit.SECONDS.sleep(5);
        
        instances = naming.getAllInstances(serviceName);
        
        Assert.assertEquals(0, instances.size());
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
        
        List<Instance> instances;
        instances = naming.getAllInstances(serviceName);
        verifyInstanceList(instances, 1, serviceName);
        
        Assert.assertEquals(1, instances.size());
        
        naming.deregisterInstance(serviceName, "127.0.0.1", TEST_PORT, "c1");
        
        TimeUnit.SECONDS.sleep(2);
        
        instances = naming.getAllInstances(serviceName);
        
        Assert.assertEquals(0, instances.size());
    }
    
    private void verifyInstanceList(List<Instance> instances, int size, String serviceName) throws Exception {
        int i = 0;
        while (i < 20) {
            instances = naming.getAllInstances(serviceName);
            if (instances.size() == size) {
                break;
            } else {
                TimeUnit.SECONDS.sleep(3);
                i++;
            }
        }
    }
    
    @Test
    public void regService() throws NacosException, InterruptedException {
        
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:" + port);
        properties.put(PropertyKeyConst.NAMESPACE, "t3");
        properties.put(PropertyKeyConst.CONTEXT_PATH, contextPath);
        
        naming = NamingFactory.createNamingService(properties);
        TimeUnit.SECONDS.sleep(10);
        
        String serviceName = "dungu.test.10";
        naming.registerInstance(serviceName, "127.0.0.1", 80, "c1");
        naming.registerInstance(serviceName, "127.0.0.2", 80, "c2");
        List<Instance> instances = naming.getAllInstances(serviceName);
        
        Assert.assertEquals(1, instances.size());
    }
    
    /**
     * 注册一个默认cluster的Instance，并验证
     *
     * @throws Exception
     */
    @Test
    public void regDomTest() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        System.out.println(serviceName);
        naming.registerInstance(serviceName, NamingBase.TEST_IP_4_DOM_1, NamingBase.TEST_PORT);
        
        TimeUnit.SECONDS.sleep(3);
        
        List<Instance> instances = naming.getAllInstances(serviceName);
        
        Assert.assertEquals(1, instances.size());
        Assert.assertEquals(instances.get(0).getIp(), NamingBase.TEST_IP_4_DOM_1);
        Assert.assertEquals(instances.get(0).getPort(), NamingBase.TEST_PORT);
    }
    
    /**
     * 注册一个自定义cluster的Instance，并验证
     *
     * @throws Exception
     */
    @Test
    public void regDomClusterTest() throws Exception {
        
        String serviceName = NamingBase.randomDomainName();
        
        System.out.println(serviceName);
        
        naming.registerInstance(serviceName, NamingBase.TEST_IP_4_DOM_1, NamingBase.TEST_PORT,
                NamingBase.TEST_NEW_CLUSTER_4_DOM_1);
        
        TimeUnit.SECONDS.sleep(3);
        
        List<Instance> instances = naming.getAllInstances(serviceName);
        
        Assert.assertEquals(1, instances.size());
        Assert.assertEquals(instances.get(0).getIp(), NamingBase.TEST_IP_4_DOM_1);
        Assert.assertEquals(instances.get(0).getPort(), NamingBase.TEST_PORT);
        
        List<Instance> instances2 = naming
                .getAllInstances(serviceName, Arrays.asList(NamingBase.TEST_NEW_CLUSTER_4_DOM_1));
        
        Assert.assertEquals(instances2.size(), 1);
        Assert.assertEquals(instances2.get(0).getIp(), NamingBase.TEST_IP_4_DOM_1);
        Assert.assertEquals(instances2.get(0).getPort(), NamingBase.TEST_PORT);
    }
    
    /**
     * 注册一个自定义的Instance，并验证
     *
     * @throws Exception
     */
    @Test
    public void regDomWithInstance() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        
        Instance i1 = NamingBase.getInstance(serviceName);
        naming.registerInstance(serviceName, i1);
        
        TimeUnit.SECONDS.sleep(3);
        
        List<Instance> instances = naming.getAllInstances(serviceName);
        
        Assert.assertEquals(instances.size(), 1);
        
        Assert.assertTrue(NamingBase.verifyInstance(i1, instances.get(0)));
        
    }
    
    /**
     * 注册一个不健康的Instance，并验证
     *
     * @throws Exception
     */
    @Test
    public void regDomNotHealth() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        System.out.println(serviceName);
        
        naming.registerInstance(serviceName, "1.1.1.1", 2000);
        naming.registerInstance(serviceName, NamingBase.TEST_IP_4_DOM_1, NamingBase.TEST_PORT);
        
        TimeUnit.SECONDS.sleep(3);
        
        List<Instance> instances = naming.selectInstances(serviceName, false);
        
        Assert.assertEquals(0, instances.size());
    }
    
    @Test
    public void regServiceWithMetadata() throws Exception {
        
        String serviceName = NamingBase.randomDomainName();
        System.out.println(serviceName);
        
        Instance instance = new Instance();
        instance.setIp("1.1.1.2");
        instance.setPort(9999);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("version", "1.0");
        metadata.put("env", "prod");
        instance.setMetadata(metadata);
        
        naming.registerInstance(serviceName, instance);
        
        TimeUnit.SECONDS.sleep(3);
        
        List<Instance> instances = naming.getAllInstances(serviceName);
        
        Assert.assertEquals(1, instances.size());
        Assert.assertEquals("1.0", instances.get(0).getMetadata().get("version"));
        Assert.assertEquals("prod", instances.get(0).getMetadata().get("env"));
    }
    
    @Test
    public void regServiceWithTTL() throws Exception {
        
        String serviceName = NamingBase.randomDomainName();
        System.out.println(serviceName);
        
        Instance instance = new Instance();
        instance.setIp("1.1.1.2");
        instance.setPort(9999);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put(PreservedMetadataKeys.HEART_BEAT_INTERVAL, "3");
        metadata.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "6");
        metadata.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "9");
        instance.setMetadata(metadata);
        
        naming.registerInstance(serviceName, instance);
        
        TimeUnit.SECONDS.sleep(3);
        
        List<Instance> instances = naming.getAllInstances(serviceName);
        
        Assert.assertEquals(1, instances.size());
        
        naming.deregisterInstance(serviceName, instance);
        
        TimeUnit.SECONDS.sleep(12);
        
        instances = naming.getAllInstances(serviceName);
        
        Assert.assertEquals(0, instances.size());
    }
}
