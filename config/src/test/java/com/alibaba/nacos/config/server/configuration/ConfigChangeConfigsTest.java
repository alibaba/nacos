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

package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class ConfigChangeConfigsTest {

    private static final boolean TEST_WEBHOOK_ENABLED = false;

    private static final String TEST_WEBHOOK_URL = "http://localhost:8080/webhook/putEvent?token=***";

    private static final int TEST_WEBHOOK_CONTENT_MAX_CAPACITY = 10 * 1024;

    private static final boolean TEST_FILEFORMATCHECK_ENABLED = false;

    private static final boolean TEST_WHITELIST_ENABLED = false;

    private static final String TEST_WEBHOOK_SUFFIXS = "text,yaml,html";

    private ConfigChangeConfigs configChangeConfigs;

    private MockEnvironment environment;

    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        environment.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED, "true");
        environment.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL, "http://sample.com/webhook");
        environment.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_CONTENT_MAX_CAPACITY, "10240");
        environment.setProperty(ConfigChangeConstants.FileFormatCheck.NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_ENABLED, "true");
        environment.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED, "true");
        environment.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_SUFFIXS, "yaml,xml");
        configChangeConfigs = new ConfigChangeConfigs();
    }

    @Test
    public void testUpgradeFromEvent() {
        environment.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED,
                String.valueOf(TEST_WEBHOOK_ENABLED));
        environment.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL,
                TEST_WEBHOOK_URL);
        environment.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_CONTENT_MAX_CAPACITY,
                String.valueOf(TEST_WEBHOOK_CONTENT_MAX_CAPACITY));
        environment.setProperty(ConfigChangeConstants.FileFormatCheck.NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_ENABLED,
                String.valueOf(TEST_FILEFORMATCHECK_ENABLED));
        environment.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED,
                String.valueOf(TEST_WHITELIST_ENABLED));
        environment.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_SUFFIXS,
                TEST_WEBHOOK_SUFFIXS);

        configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        assertEquals(TEST_WEBHOOK_ENABLED, configChangeConfigs.isWebHookEnabled());
        assertEquals(TEST_WEBHOOK_URL, configChangeConfigs.getWebHookUrl());
        assertEquals(TEST_WEBHOOK_CONTENT_MAX_CAPACITY, configChangeConfigs.getWebHookMaxContentCapacity());
        assertEquals(TEST_WHITELIST_ENABLED, configChangeConfigs.isWhiteListEnabled());
        assertEquals(TEST_WEBHOOK_SUFFIXS, configChangeConfigs.getWhiteListSuffixs());
        assertEquals(TEST_FILEFORMATCHECK_ENABLED, configChangeConfigs.isFileFormatCheckEnabled());
    }

    @Test
    public void testGetPropertiesByType() {
        configChangeConfigs.onEvent(ServerConfigChangeEvent.newEvent());
        String configPluginServiceType = "webhook";
        Properties properties = configChangeConfigs.getPluginProperties(configPluginServiceType);
        assertEquals(configChangeConfigs.isWebHookEnabled(), Boolean.valueOf(properties.getProperty("enabled")));
        configPluginServiceType = "fileformatcheck";
        properties = configChangeConfigs.getPluginProperties(configPluginServiceType);
        assertEquals(configChangeConfigs.isFileFormatCheckEnabled(), Boolean.valueOf(properties.getProperty("enabled")));
        configPluginServiceType = "whitelist";
        properties = configChangeConfigs.getPluginProperties(configPluginServiceType);
        assertEquals(configChangeConfigs.isWhiteListEnabled(), Boolean.valueOf(properties.getProperty("enabled")));
    }
}
