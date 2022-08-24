/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.config.impl;

import com.alibaba.nacos.plugin.config.ConfigChangePluginManager;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.plugin.config.model.ConfigChangeHandleReport;
import com.alibaba.nacos.plugin.config.spi.ConfigChangeService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;
import java.util.PriorityQueue;

/**
 * @author liyunfei
 */
public class ConfigChangePluginManagerTests {
    
    @Before
    public void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED, "true");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_WAY, "nacos");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL,
                "http://localhost:8080/webhook/send");
        // optional if webhook_url is not empty,will push to url;if not will push the endpoint
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ACCESS_KEY_ID,
                "LTAI5t9pwBXagE9vWP4ZRs1i");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ACCESS_KEY_SECRET,
                "OGWpp3FpvsgRUNicLeXI1tODmn0ajN");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENDPOINT,
                "1017438417648207.eventbridge.cn-hangzhou.aliyuncs.com");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_EVENT_BUS, "demo-bus");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_SOURCE, "webhook.event");
        
        env.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED, "true");
        env.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_WAY, "nacos");
        env.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_URLS, "");
        env.setProperty(ConfigChangeConstants.FileFormatCheck.NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_ENABLED, "true");
        
        EnvUtil.setEnvironment(env);
    }
    
    @Test
    public void testInstance() {
        ConfigChangePluginManager instance = ConfigChangePluginManager.getInstance();
        Assert.assertNotNull(instance);
    }
    
    @Test
    public void testJoin() {
        ConfigChangePluginManager.join(new ConfigChangeService() {
            @Override
            public Object execute(ProceedingJoinPoint pjp, ConfigChangeHandleReport configChangeHandleReport)
                    throws Throwable {
                return pjp.proceed();
            }
            
            @Override
            public int getOrder() {
                return 0;
            }
            
            @Override
            public String executeType() {
                return "aysnc";
            }
            
            @Override
            public String getImplWay() {
                return "self";
            }
            
            @Override
            public String getServiceType() {
                return "testPluginService";
            }
        });
        Assert.assertNotNull(ConfigChangePluginManager.getInstance().findPluginServiceImpl("self:testPluginService"));
    }
    
    @Test
    public void testFindPluginServiceQueueByPointcut() {
        PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut("import");
        Assert.assertNotNull(configChangeServicePriorityQueue);
        configChangeServicePriorityQueue = ConfigChangePluginManager.findPluginServiceQueueByPointcut("remove");
        Assert.assertNotNull(configChangeServicePriorityQueue);
        configChangeServicePriorityQueue = ConfigChangePluginManager.findPluginServiceQueueByPointcut("import");
        Assert.assertNotNull(configChangeServicePriorityQueue);
    }
    
    @Test
    public void testFindPluginServiceImpl() {
        Optional<ConfigChangeService> configChangeServiceOptional = ConfigChangePluginManager.getInstance()
                .findPluginServiceImpl("nacos:webhook");
        Assert.assertTrue(configChangeServiceOptional.isPresent());
    }
}
