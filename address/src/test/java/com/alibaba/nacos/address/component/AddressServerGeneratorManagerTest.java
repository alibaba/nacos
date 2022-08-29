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

package com.alibaba.nacos.address.component;

import com.alibaba.nacos.address.constant.AddressServerConstants;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AddressServerGeneratorManagerTest {
    
    @Test
    public void testGenerateProductName() {
        AddressServerGeneratorManager manager = new AddressServerGeneratorManager();
        final String blankName = manager.generateProductName("");
        Assert.assertEquals(AddressServerConstants.ALIWARE_NACOS_DEFAULT_PRODUCT_NAME, blankName);
    
        final String defaultName = manager.generateProductName(AddressServerConstants.DEFAULT_PRODUCT);
        Assert.assertEquals(AddressServerConstants.ALIWARE_NACOS_DEFAULT_PRODUCT_NAME, defaultName);
    
        final String testName = manager.generateProductName("test");
        Assert.assertEquals("nacos.as.test", testName);
    
    }
    
    @Test
    public void testGenerateInstancesByIps() {
        AddressServerGeneratorManager manager = new AddressServerGeneratorManager();
        final List<Instance> empty = manager.generateInstancesByIps(null, null, null, null);
        Assert.assertNotNull(empty);
        Assert.assertTrue(empty.isEmpty());
    
        String[] ipArray = new String[]{"192.168.3.1:8848", "192.168.3.2:8848", "192.168.3.3:8848"};
        final List<Instance> instanceList = manager.generateInstancesByIps("DEFAULT_GROUP@@nacos.as.test", "test", "test",
                ipArray);
        Assert.assertNotNull(instanceList);
        Assert.assertFalse(instanceList.isEmpty());
        Assert.assertEquals(3, instanceList.size());
    
        final Instance instance1 = instanceList.get(0);
        Assert.assertEquals("192.168.3.1", instance1.getIp());
    
        final Instance instance2 = instanceList.get(1);
        Assert.assertEquals("192.168.3.2", instance2.getIp());
    
        final Instance instance3 = instanceList.get(2);
        Assert.assertEquals("192.168.3.3", instance3.getIp());
    
    }
    
    @Test
    public void testGenerateResponseIps() {
        final List<com.alibaba.nacos.api.naming.pojo.Instance> instanceList = new ArrayList<>();
        Instance instance1 = new Instance();
        instance1.setIp("192.168.3.1");
        instance1.setPort(8848);
    
        Instance instance2 = new Instance();
        instance2.setIp("192.168.3.2");
        instance2.setPort(8848);
        
        Instance instance3 = new Instance();
        instance3.setIp("192.168.3.3");
        instance3.setPort(8848);
    
        instanceList.add(instance1);
        instanceList.add(instance2);
        instanceList.add(instance3);
        
        AddressServerGeneratorManager manager = new AddressServerGeneratorManager();
        final String ipListStr = manager.generateResponseIps(instanceList);
        
        StringBuilder expectStr = new StringBuilder();
        final StringBuilder ret = expectStr
                .append("192.168.3.1:8848").append('\n')
                .append("192.168.3.2:8848").append('\n')
                .append("192.168.3.3:8848").append('\n');
        Assert.assertEquals(ret.toString(), ipListStr);
    
    }
    
    @Test
    public void testGenerateNacosServiceName() {
        AddressServerGeneratorManager manager = new AddressServerGeneratorManager();
    
        final String containDefault = manager.generateNacosServiceName("DEFAULT_GROUP@@test");
        Assert.assertEquals("DEFAULT_GROUP@@test", containDefault);
    
        final String product = manager.generateNacosServiceName("product");
        Assert.assertEquals("DEFAULT_GROUP@@product", product);
    }
    
}
