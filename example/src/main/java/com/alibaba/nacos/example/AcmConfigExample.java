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

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.listener.impl.PropertiesListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Acm Config service example.
 *
 * @author Nacos
 */
public class AcmConfigExample {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AcmConfigExample.class);
    
    public static void main(String[] args) throws NacosException {
        
        Properties properties = new Properties();
        // 指定配置的 DataID 和 Group
        // 从控制台命名空间管理的"命名空间详情"中拷贝 endpoint、namespace
        properties.put(PropertyKeyConst.ENDPOINT, "acm.aliyun.com");
        properties.put(PropertyKeyConst.NAMESPACE, "xxxxxx");
        // 推荐使用 RAM 账号的 accessKey、secretKey，
        properties.put(PropertyKeyConst.ACCESS_KEY, "xxxxx");
        properties.put(PropertyKeyConst.SECRET_KEY, "xxxxxxxxxxxx");
        // 如果是加密配置，则添加下面两行进行自动解密
        properties.put("openKMSFilter", true);
        properties.put("regionId", "cn-shanghai");
        
        //如果是内网使用的话用下面的配置，设置regionId对应的内网kmsEndpoint(支持-D参数)就行了，endpoint列表：https://help.aliyun.com/document_detail/69006.html
        //properties.put("ksmEndpoint", "kms-vpc.cn-shanghai.aliyuncs.com");
        
        //Access ACM with instance RAM role: https://help.aliyun.com/document_detail/72013.html
        //properties.put("ramRoleName", "new-acm-role-test");
        
        String dataId = "cipher-kms-aes-128-test";
        String group = "DEFAULT_GROUP";
        String content = "{\"name\":\"test\",\"id\":\"333\"}";
        ConfigService configService = NacosFactory.createConfigService(properties);
        // 发布配置
        //        boolean publishConfig = configService.publishConfig(dataId, group, content);
        //        LOGGER.info("publishConfig: {}", publishConfig);
        //        wait2Sync();
        // 查询配置
        String config = configService.getConfig(dataId, group, 5000);
        LOGGER.info("getConfig: {}", config);
        // 监听配置
        configService.addListener(dataId, group, new PropertiesListener() {
            @Override
            public void innerReceive(Properties properties) {
                LOGGER.info("innerReceive: {}", properties);
            }
        });
        // 更新配置
        //        boolean updateConfig = configService.publishConfig(dataId, group, "connectTimeoutInMills=3000");
        //        LOGGER.info("updateConfig: {}", updateConfig);
        //        wait2Sync();
        // 删除配置
        //        boolean removeConfig = configService.removeConfig(dataId, group);
        //        LOGGER.info("removeConfig: {}", removeConfig);
        
        config = configService.getConfig(dataId, group, 5000);
        LOGGER.info("getConfig: {}", config);
        
        wait2Sync();
    }
    
    private static void wait2Sync() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
