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
import com.alibaba.nacos.api.config.remote.request.cluster.ConfigChangeClusterSyncRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;

@Ignore
public class ConfigTest {
    
    private static ConfigService configService;
    
    @Before
    public void before() throws Exception {
        Properties properties = new Properties();
        //properties.setProperty(PropertyKeyConst.SERVER_ADDR, "11.160..148:8848,127.0.0.1:8848,127.0.0.1:8848");
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848,127.0.0.1:8848");
    
        //properties.setProperty(PropertyKeyConst.SERVER_ADDR, "11.160.144.148:8848");
        //"11.239.114.187:8848,,11.239.113.204:8848,11.239.112.161:8848");
        //"11.239.114.187:8848");
        configService = NacosFactory.createConfigService(properties);
        //Thread.sleep(2000L);
    }
    
    @Test
    public void test222() throws Exception {
        RpcClient client = RpcClientFactory.createClient("1234", ConnectionType.RSOCKET);
        client.init(new ServerListFactory() {
            @Override
            public String genNextServer() {
                return "127.0.0.1:8848";
            }
            
            @Override
            public String getCurrentServer() {
                return "127.0.0.1:8848";
            }
        });
        client.start();
        ConfigChangeClusterSyncRequest syncRequest = new ConfigChangeClusterSyncRequest();
        syncRequest.setDataId("xiaochun.xxc1");
        syncRequest.setGroup("xiaochun.xxc");
        syncRequest.setIsBeta("N");
        syncRequest.setLastModified(System.currentTimeMillis());
        syncRequest.setTag("");
        syncRequest.setTenant("");
        System.out.println(client.isRunning());
        Response response = client.request(syncRequest);
        client.request(syncRequest);
        
        client.request(syncRequest);
        System.out.println(response);
        
    }
    
    @After
    public void cleanup() throws Exception {
        configService.shutDown();
    }
    
    @Test
    public void test2() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "11.160.144.148:8848");
        //"
        System.out.println("1");
        List<ConfigService> configServiceList = new ArrayList<ConfigService>();
        for (int i = 0; i < 200; i++) {
    
            ConfigService configService = NacosFactory.createConfigService(properties);
            configService.addListener("test", "test", new AbstractListener() {
    
                @Override
                public void receiveConfigInfo(String configInfo) {
                    System.out.println("listener2:" + configInfo);
                }
            });
            configServiceList.add(configService);
        }
        System.out.println("2");
    
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
    
                Random random = new Random();
                int times = 10000;
                while (times > 0) {
                    try {
                        System.out.println("3");
    
                        boolean result = configService
                                .publishConfig("test", "test", "value" + System.currentTimeMillis());
    
                        times--;
                        Thread.sleep(10000L);
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
    
        Random random = new Random();
        final String dataId = "xiaochun.xxc";
        final String group = "xiaochun.xxc";
        final String content = "lessspring-" + System.currentTimeMillis();
    
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                Random random = new Random();
                int times = 1000;
                while (times > 0) {
                    try {
                        boolean success = configService.publishConfig(dataId + random.nextInt(20), group,
                                "value" + System.currentTimeMillis());
                        times--;
                        Thread.sleep(2000L);
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
                System.out.println("receiveConfigInfo1 :" + configInfo);
            }
        };
    
        for (int i = 0; i < 20; i++) {
            configService.getConfigAndSignListener(dataId + i, group, 3000L, listener);
        }
    
        Thread.sleep(10000L);
    
        for (int i = 0; i < 20; i++) {
            //configService.removeListener(dataId + i, group, listener);
        }
        //System.out.println("remove listens.");
        
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
