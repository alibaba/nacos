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
import com.alibaba.nacos.plugin.config.constants.ConfigChangeExecuteTypes;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;
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
 * Tests for config change plugin manager.
 *
 * @author liyunfei
 */
public class ConfigChangePluginManagerTests {
    
    @Before
    public void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED, "true");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL,
                "hhttp://***/webhook/putEvents");
        env.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED, "true");
        env.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_URLS, "text,xml,html");
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
            public void execute(ProceedingJoinPoint pjp, ConfigChangeRequest configChangeRequest,
                    ConfigChangeResponse configChangeResponse) throws Throwable {
                System.out.println("before execute");
                pjp.proceed();
            }
            
            @Override
            public int getOrder() {
                return 0;
            }
            
            @Override
            public ConfigChangeExecuteTypes executeType() {
                return ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE;
            }
            
            @Override
            public String getServiceType() {
                return "testpluginservice";
            }
        });
        Assert.assertNotNull(ConfigChangePluginManager.getInstance().findPluginServiceImpl("testpluginservice"));
    }
    
    @Test
    public void testFindPluginServiceQueueByPointcut() {
        PriorityQueue<ConfigChangeService> configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut(ConfigChangePointCutTypes.PUBLISH_BY_HTTP);
        Assert.assertNotNull(configChangeServicePriorityQueue);
        configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut(ConfigChangePointCutTypes.REMOVE_BY_RPC);
        Assert.assertNotNull(configChangeServicePriorityQueue);
        configChangeServicePriorityQueue = ConfigChangePluginManager
                .findPluginServiceQueueByPointcut(ConfigChangePointCutTypes.IMPORT_BY_HTTP);
        Assert.assertNotNull(configChangeServicePriorityQueue);
    }
    
    @Test
    public void testFindPluginServiceImpl() {
        Optional<ConfigChangeService> configChangeServiceOptional = ConfigChangePluginManager.getInstance()
                .findPluginServiceImpl("webhook");
        Assert.assertTrue(configChangeServiceOptional.isPresent());
    }
}