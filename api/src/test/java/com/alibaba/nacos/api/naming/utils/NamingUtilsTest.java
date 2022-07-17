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
import static org.junit.Assert.assertTrue;

public class NamingUtilsTest {
    
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
            assertTrue(NacosException.class.equals(e.getClass()));
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
            assertTrue(NacosException.class.equals(e.getClass()));
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
            assertTrue(NacosException.class.equals(e.getClass()));
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
            assertTrue(NacosException.class.equals(e.getClass()));
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
}