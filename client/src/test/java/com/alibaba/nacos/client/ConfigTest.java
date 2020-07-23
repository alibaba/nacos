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
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Properties;
import java.util.Scanner;

@Ignore
public class ConfigTest {
    
    private static ConfigService configService;
    
    @Before
    public void before() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:28848");
        //"11.239.114.187:8848,11.239.113.204:8848,11.239.112.161:8848");
        //"11.239.114.187:8848");
        configService = NacosFactory.createConfigService(properties);
        //Thread.sleep(2000L);
    }
    
    @After
    public void cleanup() throws Exception {
        configService.shutDown();
    }
    
    @Test
    public void test2() throws Exception {
        
        Thread.sleep(1000000L);
    }
    
    @Test
    public void test() throws Exception {
    
        final String dataId = "lessspring";
        final String group = "lessspring";
        final String content = "lessspring-" + System.currentTimeMillis();
        System.out.println("4-" + System.currentTimeMillis());
    
        boolean result = configService.publishConfig(dataId, group, content);
        //Assert.assertTrue(result);
        System.out.println("5-" + System.currentTimeMillis());
    
        configService.getConfigAndSignListener(dataId, group, 5000, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("receiveConfigInfo1 :" + configInfo);
            }
        });
    
        //configService.removeConfig(dataId, group);
    
        configService.publishConfig("lessspring2", group, "lessspring2value");
    
        configService.getConfigAndSignListener("lessspring2", group, 5000, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("receiveConfigInfo2 :" + configInfo);
            }
        });
    
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
        System.out.println("4-" + System.currentTimeMillis());
        
        boolean result = configService.publishConfig(dataId, group, content);
        //Assert.assertTrue(result);
        System.out.println("5-" + System.currentTimeMillis());
        
        configService.getConfigAndSignListener(dataId, group, 5000, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("receiveConfigInfo1 :" + configInfo);
            }
        });
        
        //configService.removeConfig(dataId, group);
        
        configService.publishConfig("lessspring2", group, "lessspring2value");
        
        configService.getConfigAndSignListener("lessspring2", group, 5000, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("receiveConfigInfo2 :" + configInfo);
            }
        });
        
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
