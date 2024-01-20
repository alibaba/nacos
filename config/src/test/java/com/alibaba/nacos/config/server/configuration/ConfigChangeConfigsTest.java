/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Nacos config change configs test.
 *
 * @author liyunfei
 **/
public class ConfigChangeConfigsTest {
    
    private ConfigChangeConfigs configChangeConfigs;
    
    private MockEnvironment environment;
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        environment.setProperty("nacos.core.config.plugin.mockPlugin.enabled", "true");
        EnvUtil.setEnvironment(environment);
        configChangeConfigs = new ConfigChangeConfigs();
    }
    
    @Test
    public void testEnable() {
        Assert.assertTrue(Boolean.parseBoolean(configChangeConfigs
                .getPluginProperties("mockPlugin").getProperty("enabled")));
    }
    
    @Test
    public void testUpgradeEnable() {
        environment.setProperty("nacos.core.config.plugin.mockPlugin.enabled", "false");
        configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        Assert.assertFalse(Boolean.parseBoolean(configChangeConfigs
                .getPluginProperties("mockPlugin").getProperty("enabled")));
    }
    
}
