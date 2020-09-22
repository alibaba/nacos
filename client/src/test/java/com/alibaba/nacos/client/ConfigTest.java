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

package com.alibaba.nacos.client;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;

import static com.alibaba.nacos.api.common.Constants.LINE_SEPARATOR;
import static com.alibaba.nacos.api.common.Constants.WORD_SEPARATOR;

@Ignore
public class ConfigTest {
    
    private static ConfigService configService;
    
    @Before
    public void before() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
    
        //properties.setProperty(PropertyKeyConst.SERVER_ADDR, "11.160.144.149:8848");
        //properties.setProperty(PropertyKeyConst.SERVER_ADDR, "11.160.67.159:8849");
        
        //properties.setProperty(PropertyKeyConst.SERVER_ADDR, "11.160.144.149:8848,11.160.144.148:8848,127.0.0.1:8848");
        //"11.239.114.187:8848,,11.239.113.204:8848,11.239.112.161:8848");
        //"11.239.114.187:8848");
        properties.setProperty(PropertyKeyConst.USERNAME, "nacos");
        properties.setProperty(PropertyKeyConst.PASSWORD, "nacos");
    
        configService = NacosFactory.createConfigService(properties);
        //Thread.sleep(2000L);
    }
    
    @Test
    public void test222() throws Exception {
        Map<String, String> labels = new HashMap<String, String>();
        labels.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_CLUSTER);
        
        RpcClient client = RpcClientFactory.createClient("1234", ConnectionType.RSOCKET, labels);
        client.init(new ServerListFactory() {
            @Override
            public String genNextServer() {
                return "11.160.144.148:8848";
            }
            
            @Override
            public String getCurrentServer() {
                return "11.160.144.148:8848";
            }
    
            @Override
            public List<String> getServerList() {
                return Lists.newArrayList("11.160.144.148:8848");
            }
    
        });
        //client.start();
    
        ConfigBatchListenRequest syncRequest = new ConfigBatchListenRequest();
        syncRequest.setListen(true);
        final String dataId = "xiaochun.xxc";
        final String group = "xiaochun.xxc";
        long start = System.currentTimeMillis();
        System.out.println("100K start send 100 request...");
    
        for (int i = 0; i < 100; i++) {
            StringBuilder listenConfigsBuilder = new StringBuilder();
            listenConfigsBuilder.append(dataId + i).append(WORD_SEPARATOR);
            listenConfigsBuilder.append(group).append(WORD_SEPARATOR);
            listenConfigsBuilder.append(new String(new byte[100])).append(WORD_SEPARATOR);
            listenConfigsBuilder.append("default").append(LINE_SEPARATOR);
            if (i == 10) {
                System.out.println("单个报文大小长度：" + listenConfigsBuilder.toString().length());
            }
            Response request = client.request(syncRequest);
        }
        long end = System.currentTimeMillis();
        System.out.println("total cost:" + (end - start));
    
        StringBuilder listenConfigsBuilder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            listenConfigsBuilder.append(dataId + i).append(WORD_SEPARATOR);
            listenConfigsBuilder.append(group).append(WORD_SEPARATOR);
            listenConfigsBuilder.append(new String(new byte[100000])).append(WORD_SEPARATOR);
            listenConfigsBuilder.append("default").append(LINE_SEPARATOR);
        }
        System.out.println("100K start send batch 100 request...");
        long start2 = System.currentTimeMillis();
        System.out.println("总报文大小长度：" + listenConfigsBuilder.toString().length());
        Response response = client.request(syncRequest);
        long end2 = System.currentTimeMillis();
        System.out.println("toal cost:" + (end2 - start2));
        
        Thread.sleep(50000000L);
        
    }
    
    @Test
    public void test333() throws Exception {
        Map<String, String> labels = new HashMap<String, String>();
        labels.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
    
        RpcClient client = RpcClientFactory.createClient("1234", ConnectionType.RSOCKET, labels);
        client.init(new ServerListFactory() {
            @Override
            public String genNextServer() {
                return "127.0.0.1:8848";
            }
            
            @Override
            public String getCurrentServer() {
                return "127.0.0.1:8848";
            }
            
            @Override
            public List<String> getServerList() {
                return Lists.newArrayList("127.0.0.1:8848");
            }
            
        });
        client.start();
        
        ConfigBatchListenRequest syncRequest = new ConfigBatchListenRequest();
        syncRequest.setListen(true);
        final String dataId = "xiaochun.xxc";
        final String group = "xiaochun.xxc";
        syncRequest.addConfigListenContext(group, dataId, null, null);
        long start = System.currentTimeMillis();
        System.out.println("send :" + System.currentTimeMillis());
    
        RequestFuture requestFuture = client.requestFuture(syncRequest);
        while (true) {
            Thread.sleep(1L);
            System.out.println(requestFuture.isDone());
            if (requestFuture.isDone()) {
                System.out.println(requestFuture.get());
                break;
            }
        }
        
        Thread.sleep(10000L);
        
    }
    
    @After
    public void cleanup() throws Exception {
        configService.shutDown();
    }
    
    @Test
    public void test2() throws Exception {
        final String dataId = "xiaochun.xxc";
        final String group = "xiaochun.xxc";
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "11.160.144.149:8848");
        //"
        List<ConfigService> configServiceList = new ArrayList<ConfigService>();
        for (int i = 0; i < 300; i++) {
            
            ConfigService configService = NacosFactory.createConfigService(properties);
    
            Listener listener = new AbstractListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    System.out.println(
                            "receiveConfigInfo1 content:" + (System.currentTimeMillis() - Long.valueOf(configInfo)));
    
                }
            };
    
            configService.addListener(dataId, group, listener);
            configServiceList.add(configService);
            System.out.println(configServiceList.size());
        }
        System.out.println("2");
    
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
    
                Random random = new Random();
                int times = 10000;
                while (times > 0) {
                    try {
                        boolean result = configService.publishConfig(dataId, group, "" + System.currentTimeMillis());
                        
                        times--;
                        Thread.sleep(1000L);
                    } catch (Exception e) {
                        e.printStackTrace();
    
                    }
                }
            }
        
        });
        th.start();
        
        Thread.sleep(1000000L);
    }
    
    @Test
    public void test() throws Exception {
    
        //SnapShotSwitch.setIsSnapShot(false);
        final Random random = new Random();
        final String dataId = "xiaochun.xxc";
        final String group = "xiaochun.xxc";
        
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                int times = 1000;
                while (times > 0) {
                    try {
                        String content1 = System.currentTimeMillis() + "";
                        boolean b = configService.publishConfig(dataId + random.nextInt(20), group, content1);
                        times--;
                        Thread.sleep(1000L);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
    
                System.out.println(times);
                System.out.println("Write Done");
            }
    
        });
    
        th.start();
        
        Listener listener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println(
                        "receiveConfigInfo1 content:" + (System.currentTimeMillis() - Long.valueOf(configInfo)));
                
            }
        };
        
        for (int i = 0; i < 20; i++) {
            final int ls = i;
            configService.addListener(dataId + i, group, listener);
            
        }
    
        Thread.sleep(1000000L);
        
        for (int i = 0; i < 20; i++) {
            configService.removeListener(dataId + i, group, listener);
        }
        System.out.println("remove listens.");
        
        Scanner scanner = new Scanner(System.in);
        System.out.println("input content");
        while (scanner.hasNextLine()) {
            String s = scanner.next();
            if ("exit".equals(s)) {
                scanner.close();
                return;
            }
            configService.publishConfig(dataId, group, s);
        }
    }
    
    @Test
    public void test3() throws Exception {
        
        final String dataId = "lessspring";
        final String group = "lessspring";
        final String content = "lessspring-" + System.currentTimeMillis();
        
        boolean result = configService.publishConfig(dataId, group, content);
        //Assert.assertTrue(result);
    
        Listener listener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("receiveConfigInfo1 :" + configInfo);
            }
        };
    
        configService.getConfigAndSignListener(dataId, group, 5000, listener);
    
        System.out.println("Add Listen config..");
    
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                Random random = new Random();
                int times = 100;
                while (times > 0) {
                    try {
                        configService.publishConfig(dataId, group, "value" + System.currentTimeMillis());
    
                        times--;
                        Thread.sleep(5000L);
                    } catch (Exception e) {
                        e.printStackTrace();
    
                    }
                }
    
                System.out.println(times);
                System.out.println("Write Done");
            }
        
        });
    
        th.start();
        Scanner scanner = new Scanner(System.in);
        System.out.println("input content");
        while (scanner.hasNextLine()) {
            String s = scanner.next();
            if ("exit".equals(s)) {
                scanner.close();
                return;
            }
            configService.publishConfig(dataId, group, s);
        }
    }
    
}
