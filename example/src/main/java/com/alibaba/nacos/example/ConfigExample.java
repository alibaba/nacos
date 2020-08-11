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

package com.alibaba.nacos.example;

import java.util.Properties;
import java.util.concurrent.Executor;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigInfo;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * Config service example.
 *
 * @author Nacos
 */
public class ConfigExample {
    
    public static void main(String[] args) throws NacosException, InterruptedException {
        String serverAddr = "localhost:8848";
        String dataId = "example";
        String group = "DEFAULT_GROUP";
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        properties.put("username", "nacos");
        properties.put("password", "nacos");
        ConfigService configService = NacosFactory.createConfigService(properties);
        String content = configService.getConfig(dataId, group, 5000);
        System.out.println("content:" + content);
        
        ConfigInfo configInfo = configService.getConfigInfo(dataId, group, 5000);
        System.out.println("configInfo====>" + configInfo.toString());
        
        Properties configToProperties = configService.getConfigToProperties(dataId, group, 6000);
        System.out.println("configToProperties use the server configType====>" + configToProperties);
        
        Properties configToProperties2 = configService.getConfigToProperties(dataId, group, "yml", 6000);
        System.out.println("configToProperties use the custom configType====>" + configToProperties2);
        
        configService.addListener(dataId, group, new Listener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("receive:" + configInfo);
            }
            
            @Override
            public Executor getExecutor() {
                return null;
            }
        });
        
        boolean isPublishOk = configService.publishConfig(dataId, group, "content");
        System.out.println(isPublishOk);
        
        Thread.sleep(3000);
        content = configService.getConfig(dataId, group, 5000);
        System.out.println(content);
        
        boolean isRemoveOk = configService.removeConfig(dataId, group);
        System.out.println(isRemoveOk);
        Thread.sleep(3000);
        
        content = configService.getConfig(dataId, group, 5000);
        System.out.println(content);
        Thread.sleep(300000);
        
    }
}
