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

package com.alibaba.nacos.api.naming.utils;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.utils.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NamingUtilsTest {
    
    @Test
    public void testGetGroupedName() {
        assertEquals("group@@serviceName", NamingUtils.getGroupedName("serviceName", "group"));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetGroupedNameWithoutGroup() {
        NamingUtils.getGroupedName("serviceName", "");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetGroupedNameWithoutServiceName() {
        NamingUtils.getGroupedName("", "group");
    }
    
    @Test
    public void testGetServiceName() {
        String validServiceName = "group@@serviceName";
        assertEquals("serviceName", NamingUtils.getServiceName(validServiceName));
    }
    
    @Test
    public void testGetServiceNameWithoutGroup() {
        String serviceName = "serviceName";
        assertEquals(serviceName, NamingUtils.getServiceName(serviceName));
    }
    
    @Test
    public void testGetServiceNameWithEmpty() {
        assertEquals(StringUtils.EMPTY, NamingUtils.getServiceName(null));
    }
    
    @Test
    public void testGetGroupName() {
        String validServiceName = "group@@serviceName";
        assertEquals("group", NamingUtils.getGroupName(validServiceName));
    }
    
    @Test
    public void testGetGroupNameWithoutGroup() {
        String serviceName = "serviceName";
        assertEquals(Constants.DEFAULT_GROUP, NamingUtils.getGroupName(serviceName));
    }
    
    @Test
    public void testGetGroupNameWithEmpty() {
        assertEquals(StringUtils.EMPTY, NamingUtils.getGroupName(null));
    }
    
    @Test
    public void testCheckServiceNameFormat() {
        String validServiceName = "group@@serviceName";
        NamingUtils.checkServiceNameFormat(validServiceName);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCheckServiceNameFormatWithoutGroupAndService() {
        String validServiceName = "@@";
        NamingUtils.checkServiceNameFormat(validServiceName);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCheckServiceNameFormatWithoutGroup() {
        String validServiceName = "@@service";
        NamingUtils.checkServiceNameFormat(validServiceName);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCheckServiceNameFormatWithoutService() {
        String validServiceName = "group@@";
        NamingUtils.checkServiceNameFormat(validServiceName);
    }
    
    @Test
    public void testGetGroupedNameOptional() {
        String onlyGroupName = NamingUtils.getGroupedNameOptional(StringUtils.EMPTY, "groupA");
        assertEquals("groupA@@", onlyGroupName);
        
        String onlyServiceName = NamingUtils.getGroupedNameOptional("serviceA", StringUtils.EMPTY);
        assertEquals("@@serviceA", onlyServiceName);
        
        String groupNameAndServiceName = NamingUtils.getGroupedNameOptional("serviceA", "groupA");
        assertEquals("groupA@@serviceA", groupNameAndServiceName);
    }
    
    @Test
    public void testCheckInstanceIsLegal() throws NacosException {
        // check invalid clusterName
        Instance instance = new Instance();
        instance.setClusterName("cluster1,cluster2");
        try {
            NamingUtils.checkInstanceIsLegal(instance);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof NacosException);
            assertEquals(
                    "Instance 'clusterName' should be characters with only 0-9a-zA-Z-. (current: cluster1,cluster2)",
                    e.getMessage());
        }
        
        // valid clusterName
        instance.setClusterName("cluster1");
        NamingUtils.checkInstanceIsLegal(instance);
        assertTrue(true);
    
        // check heartBeatTimeout, heartBeatInterval, ipDeleteTimeout
        Map<String, String> meta = new HashMap<>();
        meta.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "1");
        meta.put(PreservedMetadataKeys.HEART_BEAT_INTERVAL, "2");
        meta.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "1");
        instance.setMetadata(meta);
        try {
            NamingUtils.checkInstanceIsLegal(instance);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof NacosException);
            assertEquals(
                    "Instance 'heart beat interval' must less than 'heart beat timeout' and 'ip delete timeout'.",
                    e.getMessage());
        }
        meta.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "3");
        meta.put(PreservedMetadataKeys.HEART_BEAT_INTERVAL, "2");
        meta.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "3");
        NamingUtils.checkInstanceIsLegal(instance);
        assertTrue(true);
    }
    
    @Test
    public void testBatchCheckInstanceIsLegal() throws NacosException {
        // check invalid clusterName
        Instance instance = new Instance();
        instance.setClusterName("cluster1,cluster2");
        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(instance);
        try {
            NamingUtils.batchCheckInstanceIsLegal(instanceList);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof NacosException);
            assertEquals(
                    "Instance 'clusterName' should be characters with only 0-9a-zA-Z-. (current: cluster1,cluster2)",
                    e.getMessage());
        }
        instanceList.remove(instance);
        
        // TODO valid clusterName
        instance.setClusterName("cluster1");
        instanceList.add(instance);
        NamingUtils.batchCheckInstanceIsLegal(instanceList);
        assertTrue(true);
        
        instanceList.remove(instance);
        
        // check heartBeatTimeout, heartBeatInterval, ipDeleteTimeout
        Map<String, String> meta = new HashMap<>();
        meta.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "1");
        meta.put(PreservedMetadataKeys.HEART_BEAT_INTERVAL, "2");
        meta.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "1");
        instance.setMetadata(meta);
        instanceList.add(instance);
        try {
            NamingUtils.batchCheckInstanceIsLegal(instanceList);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(e instanceof NacosException);
            assertEquals(
                    "Instance 'heart beat interval' must less than 'heart beat timeout' and 'ip delete timeout'.",
                    e.getMessage());
        }
        instanceList.remove(instance);
        
        meta.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "3");
        meta.put(PreservedMetadataKeys.HEART_BEAT_INTERVAL, "2");
        meta.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "3");
        instance.setMetadata(meta);
        instanceList.add(instance);
        NamingUtils.batchCheckInstanceIsLegal(instanceList);
        assertTrue(true);
    }
    
    @Test
    public void testCheckInstanceIsEphemeral() throws NacosException {
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(9089);
        instance.setEphemeral(true);
        NamingUtils.checkInstanceIsEphemeral(instance);
        try {
            instance = new Instance();
            instance.setIp("127.0.0.1");
            instance.setPort(9089);
            instance.setEphemeral(false);
            NamingUtils.checkInstanceIsEphemeral(instance);
        } catch (NacosException e) {
            Assert.assertEquals(e.getErrCode(), NacosException.INVALID_PARAM);
        }
    }
    
    @Test
    public void testIsNumber() {
        String str1 = "abc";
        assertTrue(!NamingUtils.isNumber(str1));
    
        String str2 = "123456";
        assertTrue(NamingUtils.isNumber(str2));
    }
    
    @Test
    public void testSetInstanceIdIfNeeded() {
        Instance instance = new Instance();
        NamingUtils.setInstanceIdIfNeeded(instance);
        assertNotNull(instance.getInstanceId());
        String customInsId = "customId_1";
        instance.setInstanceId(customInsId);
        assertEquals(customInsId, instance.getInstanceId());
        
        Instance instance1 = new Instance();
        Instance instance2 = new Instance();
        Instance instance3 = new Instance();
        
        NamingUtils.setInstanceIdIfNeeded(instance1);
        NamingUtils.setInstanceIdIfNeeded(instance2);
        NamingUtils.setInstanceIdIfNeeded(instance3);
        
        assertNotNull(instance1.getInstanceId());
        assertNotNull(instance2.getInstanceId());
        assertNotNull(instance3.getInstanceId());
        
        assertNotEquals(instance1.getInstanceId(), instance2.getInstanceId());
        assertNotEquals(instance1.getInstanceId(), instance3.getInstanceId());
        assertNotEquals(instance2.getInstanceId(), instance3.getInstanceId());
    }
    
    @Test
    public void testBatchSetInstanceIdIfNeeded() {
        List<Instance> instances = new ArrayList<>();
        Instance instance1 = new Instance();
        String customInsId = "customId_1";
        instance1.setInstanceId(customInsId);
        Instance instance2 = new Instance();
        Instance instance3 = new Instance();
        
        instances.add(instance1);
        instances.add(instance2);
        instances.add(instance3);
        
        NamingUtils.batchSetInstanceIdIfNeeded(instances);
        
        assertNotNull(instance1.getInstanceId());
        assertNotNull(instance2.getInstanceId());
        assertNotNull(instance3.getInstanceId());
        
        assertEquals(customInsId, instance1.getInstanceId());
        
        assertNotEquals(instance1.getInstanceId(), instance2.getInstanceId());
        assertNotEquals(instance1.getInstanceId(), instance3.getInstanceId());
        assertNotEquals(instance2.getInstanceId(), instance3.getInstanceId());
    }
}
