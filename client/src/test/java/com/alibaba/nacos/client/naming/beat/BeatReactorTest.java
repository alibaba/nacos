/*
 *
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
 *
 */

package com.alibaba.nacos.client.naming.beat;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Ignore("Nacos 2.0 do not need heart beat")
public class BeatReactorTest {
    
    @Test
    public void testConstruct() throws NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        String threadSize = "10";
        properties.put(PropertyKeyConst.NAMING_CLIENT_BEAT_THREAD_COUNT, threadSize);
        
        NamingHttpClientProxy proxy = Mockito.mock(NamingHttpClientProxy.class);
        BeatReactor beatReactor = new BeatReactor(proxy, properties);
        Field field = BeatReactor.class.getDeclaredField("executorService");
        field.setAccessible(true);
        ScheduledThreadPoolExecutor scheduledExecutorService = (ScheduledThreadPoolExecutor) field.get(beatReactor);
        Assert.assertEquals(Integer.valueOf(threadSize).intValue(), scheduledExecutorService.getCorePoolSize());
        
    }
    
    @Test
    public void testAddBeatInfo() throws NacosException, InterruptedException {
        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setServiceName("test");
        beatInfo.setIp("11.11.11.11");
        beatInfo.setPort(1234);
        beatInfo.setCluster("clusterName");
        beatInfo.setWeight(1);
        beatInfo.setMetadata(new HashMap<String, String>());
        beatInfo.setScheduled(false);
        beatInfo.setPeriod(10L);
        
        NamingHttpClientProxy proxy = Mockito.mock(NamingHttpClientProxy.class);
        BeatReactor beatReactor = new BeatReactor(proxy);
        String serviceName = "serviceName1";
        
        beatReactor.addBeatInfo(serviceName, beatInfo);
        TimeUnit.MILLISECONDS.sleep(15);
        Mockito.verify(proxy, Mockito.times(1)).sendBeat(beatInfo, false);
        
    }
    
    @Test
    public void testRemoveBeatInfo() throws InterruptedException, NacosException {
        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setServiceName("test");
        String ip = "11.11.11.11";
        beatInfo.setIp(ip);
        int port = 1234;
        beatInfo.setPort(port);
        beatInfo.setCluster("clusterName");
        beatInfo.setWeight(1);
        beatInfo.setMetadata(new HashMap<String, String>());
        beatInfo.setScheduled(false);
        beatInfo.setPeriod(10L);
        
        NamingHttpClientProxy proxy = Mockito.mock(NamingHttpClientProxy.class);
        BeatReactor beatReactor = new BeatReactor(proxy);
        String serviceName = "serviceName1";
        
        beatReactor.addBeatInfo(serviceName, beatInfo);
        TimeUnit.MILLISECONDS.sleep(15);
        Mockito.verify(proxy, Mockito.times(1)).sendBeat(beatInfo, false);
        beatReactor.removeBeatInfo(serviceName, ip, port);
        Assert.assertTrue(beatInfo.isStopped());
        Mockito.verify(proxy, Mockito.times(1)).sendBeat(beatInfo, false);
        TimeUnit.MILLISECONDS.sleep(10);
        
    }
    
    @Test
    public void testBuildBeatInfo1() {
        String ip = "11.11.11.11";
        int port = 1234;
        double weight = 1.0;
        String serviceName = "service@@group1";
        String clusterName = "cluster1";
        
        Map<String, String> meta = new HashMap<>();
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp(ip);
        instance.setPort(port);
        instance.setWeight(weight);
        instance.setMetadata(meta);
        instance.setClusterName(clusterName);
        
        BeatInfo expectInfo = new BeatInfo();
        expectInfo.setServiceName(serviceName);
        expectInfo.setIp(ip);
        expectInfo.setPort(port);
        expectInfo.setCluster(clusterName);
        expectInfo.setWeight(weight);
        expectInfo.setMetadata(meta);
        expectInfo.setScheduled(false);
        expectInfo.setPeriod(Constants.DEFAULT_HEART_BEAT_INTERVAL);
        
        NamingHttpClientProxy proxy = Mockito.mock(NamingHttpClientProxy.class);
        BeatReactor beatReactor = new BeatReactor(proxy);
        assertBeatInfoEquals(expectInfo, beatReactor.buildBeatInfo(instance));
    }
    
    @Test
    public void testBuildBeatInfo2() {
        String ip = "11.11.11.11";
        int port = 1234;
        double weight = 1.0;
        String serviceName = "service";
        String clusterName = "cluster1";
        
        Map<String, String> meta = new HashMap<>();
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp(ip);
        instance.setPort(port);
        instance.setWeight(weight);
        instance.setMetadata(meta);
        instance.setClusterName(clusterName);
        
        String groupedService = "group1@@service";
        
        BeatInfo expectInfo = new BeatInfo();
        expectInfo.setServiceName(groupedService);
        expectInfo.setIp(ip);
        expectInfo.setPort(port);
        expectInfo.setCluster(clusterName);
        expectInfo.setWeight(weight);
        expectInfo.setMetadata(meta);
        expectInfo.setScheduled(false);
        expectInfo.setPeriod(Constants.DEFAULT_HEART_BEAT_INTERVAL);
        
        NamingHttpClientProxy proxy = Mockito.mock(NamingHttpClientProxy.class);
        BeatReactor beatReactor = new BeatReactor(proxy);
        assertBeatInfoEquals(expectInfo, beatReactor.buildBeatInfo(groupedService, instance));
    }
    
    void assertBeatInfoEquals(BeatInfo expect, BeatInfo actual) {
        Assert.assertEquals(expect.getCluster(), actual.getCluster());
        Assert.assertEquals(expect.getIp(), actual.getIp());
        Assert.assertEquals(expect.getMetadata(), actual.getMetadata());
        Assert.assertEquals(expect.getPeriod(), actual.getPeriod());
        Assert.assertEquals(expect.getPort(), actual.getPort());
        Assert.assertEquals(expect.getServiceName(), actual.getServiceName());
        Assert.assertEquals(expect.getWeight(), actual.getWeight(), 0.1);
        Assert.assertEquals(expect.isStopped(), actual.isStopped());
        Assert.assertEquals(expect.isScheduled(), actual.isScheduled());
    }
    
    @Test
    public void testBuildKey() {
        String ip = "11.11.11.11";
        int port = 1234;
        String serviceName = "serviceName1";
        
        NamingHttpClientProxy proxy = Mockito.mock(NamingHttpClientProxy.class);
        BeatReactor beatReactor = new BeatReactor(proxy);
        Assert.assertEquals(
                serviceName + Constants.NAMING_INSTANCE_ID_SPLITTER + ip + Constants.NAMING_INSTANCE_ID_SPLITTER + port,
                beatReactor.buildKey(serviceName, ip, port));
    }
    
    @Test
    public void testShutdown() throws NacosException, NoSuchFieldException, IllegalAccessException {
        NamingHttpClientProxy proxy = Mockito.mock(NamingHttpClientProxy.class);
        BeatReactor beatReactor = new BeatReactor(proxy);
        Field field = BeatReactor.class.getDeclaredField("executorService");
        field.setAccessible(true);
        ScheduledThreadPoolExecutor scheduledExecutorService = (ScheduledThreadPoolExecutor) field.get(beatReactor);
        
        Assert.assertFalse(scheduledExecutorService.isShutdown());
        beatReactor.shutdown();
        Assert.assertTrue(scheduledExecutorService.isShutdown());
    }
    
    @Test
    public void testLightBeatFromResponse() throws InterruptedException, NacosException, JsonProcessingException {
        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setServiceName("test");
        beatInfo.setIp("11.11.11.11");
        beatInfo.setPort(1234);
        beatInfo.setCluster("clusterName");
        beatInfo.setWeight(1);
        beatInfo.setMetadata(new HashMap<String, String>());
        beatInfo.setScheduled(false);
        beatInfo.setPeriod(10L);
        
        NamingHttpClientProxy proxy = Mockito.mock(NamingHttpClientProxy.class);
        String jsonString = "{\"lightBeatEnabled\":true,\"clientBeatInterval\":10}";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode actualObj = mapper.readTree(jsonString);
        
        Mockito.when(proxy.sendBeat(beatInfo, false)).thenReturn(actualObj);
        BeatReactor beatReactor = new BeatReactor(proxy);
        String serviceName = "serviceName1";
        
        beatReactor.addBeatInfo(serviceName, beatInfo);
        TimeUnit.MILLISECONDS.sleep(12);
        Mockito.verify(proxy, Mockito.times(1)).sendBeat(beatInfo, false);
        TimeUnit.MILLISECONDS.sleep(12);
        Mockito.verify(proxy, Mockito.times(1)).sendBeat(beatInfo, false);
        Mockito.verify(proxy, Mockito.times(1)).sendBeat(beatInfo, true);
        
    }
    
    @Test
    public void testIntervalFromResponse() throws JsonProcessingException, NacosException, InterruptedException {
        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setServiceName("test");
        beatInfo.setIp("11.11.11.11");
        beatInfo.setPort(1234);
        beatInfo.setCluster("clusterName");
        beatInfo.setWeight(1);
        beatInfo.setMetadata(new HashMap<String, String>());
        beatInfo.setScheduled(false);
        beatInfo.setPeriod(10L);
        
        NamingHttpClientProxy proxy = Mockito.mock(NamingHttpClientProxy.class);
        String jsonString = "{\"clientBeatInterval\":20}";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode actualObj = mapper.readTree(jsonString);
        
        Mockito.when(proxy.sendBeat(beatInfo, false)).thenReturn(actualObj);
        BeatReactor beatReactor = new BeatReactor(proxy);
        String serviceName = "serviceName1";
        
        beatReactor.addBeatInfo(serviceName, beatInfo);
        TimeUnit.MILLISECONDS.sleep(12);
        Mockito.verify(proxy, Mockito.times(1)).sendBeat(beatInfo, false);
        TimeUnit.MILLISECONDS.sleep(20);
        Mockito.verify(proxy, Mockito.times(2)).sendBeat(beatInfo, false);
        
    }
    
    @Test
    public void testNotFoundFromResponse() throws JsonProcessingException, NacosException, InterruptedException {
        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setServiceName("test");
        beatInfo.setIp("11.11.11.11");
        beatInfo.setPort(1234);
        beatInfo.setCluster("clusterName");
        beatInfo.setWeight(1);
        beatInfo.setMetadata(new HashMap<String, String>());
        beatInfo.setScheduled(false);
        beatInfo.setPeriod(10L);
        
        NamingHttpClientProxy proxy = Mockito.mock(NamingHttpClientProxy.class);
        String jsonString = "{\"clientBeatInterval\":10,\"code\":20404}";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode actualObj = mapper.readTree(jsonString);
        
        Mockito.when(proxy.sendBeat(beatInfo, false)).thenReturn(actualObj);
        
        Mockito.when(proxy.sendBeat(beatInfo, false)).thenReturn(actualObj);
        BeatReactor beatReactor = new BeatReactor(proxy);
        String serviceName = "serviceName1";
        
        beatReactor.addBeatInfo(serviceName, beatInfo);
        TimeUnit.MILLISECONDS.sleep(11);
        Mockito.verify(proxy, Mockito.times(1)).sendBeat(beatInfo, false);
        
        Instance instance = new Instance();
        instance.setPort(beatInfo.getPort());
        instance.setIp(beatInfo.getIp());
        instance.setWeight(beatInfo.getWeight());
        instance.setMetadata(beatInfo.getMetadata());
        instance.setClusterName(beatInfo.getCluster());
        instance.setServiceName(beatInfo.getServiceName());
        instance.setInstanceId(null);
        instance.setEphemeral(true);
        
        Mockito.verify(proxy, Mockito.times(1))
                .registerService(beatInfo.getServiceName(), NamingUtils.getGroupName(beatInfo.getServiceName()),
                        instance);
    }
    
}
