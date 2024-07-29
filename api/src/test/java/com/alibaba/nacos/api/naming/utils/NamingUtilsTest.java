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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingUtilsTest {
    
    @Test
    void testGetGroupedName() {
        assertEquals("group@@serviceName", NamingUtils.getGroupedName("serviceName", "group"));
    }
    
    @Test
    void testGetGroupedNameWithoutGroup() {
        assertThrows(IllegalArgumentException.class, () -> {
            NamingUtils.getGroupedName("serviceName", "");
        });
    }
    
    @Test
    void testGetGroupedNameWithoutServiceName() {
        assertThrows(IllegalArgumentException.class, () -> {
            NamingUtils.getGroupedName("", "group");
        });
    }
    
    @Test
    void testGetServiceName() {
        String validServiceName = "group@@serviceName";
        assertEquals("serviceName", NamingUtils.getServiceName(validServiceName));
    }
    
    @Test
    void testGetServiceNameWithoutGroup() {
        String serviceName = "serviceName";
        assertEquals(serviceName, NamingUtils.getServiceName(serviceName));
    }
    
    @Test
    void testGetServiceNameWithEmpty() {
        assertEquals(StringUtils.EMPTY, NamingUtils.getServiceName(null));
    }
    
    @Test
    void testGetGroupName() {
        String validServiceName = "group@@serviceName";
        assertEquals("group", NamingUtils.getGroupName(validServiceName));
    }
    
    @Test
    void testGetGroupNameWithoutGroup() {
        String serviceName = "serviceName";
        assertEquals(Constants.DEFAULT_GROUP, NamingUtils.getGroupName(serviceName));
    }
    
    @Test
    void testGetGroupNameWithEmpty() {
        assertEquals(StringUtils.EMPTY, NamingUtils.getGroupName(null));
    }
    
    @Test
    void testIsServiceNameCompatibilityMode() {
        String serviceName1 = "group@@serviceName";
        assertTrue(NamingUtils.isServiceNameCompatibilityMode(serviceName1));
        
        String serviceName2 = "serviceName";
        assertFalse(NamingUtils.isServiceNameCompatibilityMode(serviceName2));
        
        String serviceName3 = null;
        assertFalse(NamingUtils.isServiceNameCompatibilityMode(serviceName3));
    }
    
    @Test
    void testCheckServiceNameFormat() {
        String validServiceName = "group@@serviceName";
        NamingUtils.checkServiceNameFormat(validServiceName);
    }
    
    @Test
    void testCheckServiceNameFormatWithoutGroupAndService() {
        assertThrows(IllegalArgumentException.class, () -> {
            String validServiceName = "@@";
            NamingUtils.checkServiceNameFormat(validServiceName);
        });
    }
    
    @Test
    void testCheckServiceNameFormatWithoutGroup() {
        assertThrows(IllegalArgumentException.class, () -> {
            String validServiceName = "@@service";
            NamingUtils.checkServiceNameFormat(validServiceName);
        });
    }
    
    @Test
    void testCheckServiceNameFormatWithoutService() {
        assertThrows(IllegalArgumentException.class, () -> {
            String validServiceName = "group@@";
            NamingUtils.checkServiceNameFormat(validServiceName);
        });
    }
    
    @Test
    void testGetGroupedNameOptional() {
        String onlyGroupName = NamingUtils.getGroupedNameOptional(StringUtils.EMPTY, "groupA");
        assertEquals("groupA@@", onlyGroupName);
        
        String onlyServiceName = NamingUtils.getGroupedNameOptional("serviceA", StringUtils.EMPTY);
        assertEquals("@@serviceA", onlyServiceName);
        
        String groupNameAndServiceName = NamingUtils.getGroupedNameOptional("serviceA", "groupA");
        assertEquals("groupA@@serviceA", groupNameAndServiceName);
    }
    
    @Test
    void testCheckInstanceIsLegal() throws NacosException {
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
            assertEquals("Instance 'heart beat interval' must less than 'heart beat timeout' and 'ip delete timeout'.",
                    e.getMessage());
        }
        meta.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "3");
        meta.put(PreservedMetadataKeys.HEART_BEAT_INTERVAL, "2");
        meta.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, "3");
        NamingUtils.checkInstanceIsLegal(instance);
        assertTrue(true);
    }
    
    @Test
    void testBatchCheckInstanceIsLegal() throws NacosException {
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
            assertEquals("Instance 'heart beat interval' must less than 'heart beat timeout' and 'ip delete timeout'.",
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
    void testCheckInstanceIsEphemeral() throws NacosException {
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
            assertEquals(NacosException.INVALID_PARAM, e.getErrCode());
        }
    }
    
    @Test
    void testCheckInstanceIsNull() throws NacosException {
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(9089);
        NamingUtils.checkInstanceIsLegal(instance);
        try {
            NamingUtils.checkInstanceIsLegal(null);
        } catch (NacosException e) {
            assertEquals(NacosException.INVALID_PARAM, e.getErrCode());
        }
    }
    
    @Test
    void testIsNumber() {
        String str1 = "abc";
        assertFalse(NamingUtils.isNumber(str1));
        
        String str2 = "123456";
        assertTrue(NamingUtils.isNumber(str2));
    }
}
