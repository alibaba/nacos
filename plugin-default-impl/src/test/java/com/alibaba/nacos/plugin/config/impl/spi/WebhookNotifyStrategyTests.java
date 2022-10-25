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

package com.alibaba.nacos.plugin.config.impl.spi;

import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.config.ConfigChangeNotifyInfoBuilder;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.impl.NacosWebHookPluginService;
import com.alibaba.nacos.plugin.config.model.ConfigChangeNotifyInfo;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.TimeUnit;

/**
 * Tests for webhook notify.
 *
 * @author liyunfei
 */
public class WebhookNotifyStrategyTests {
    
    final String selfUrl = "http://***/webhook/putEvents";
    
    final String eventUrl = "http://***/webhook/putEvents?token=***";
    
    @Before
    public void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED, "true");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL,
                "http://localhost:8080/webhook/send");
        env.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED, "true");
        env.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_URLS, "");
        env.setProperty(ConfigChangeConstants.FileFormatCheck.NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_ENABLED, "true");
        
        EnvUtil.setEnvironment(env);
    }
    
    @Test
    public void testNotify() {
        final NacosWebHookPluginService nacosWebHookPluginService = new NacosWebHookPluginService();
        ConfigChangeNotifyInfoBuilder configChangeNotifyInfoBuilder = ConfigChangeNotifyInfoBuilder.newBuilder();
        ConfigChangeNotifyInfo configChangeNotifyInfo = configChangeNotifyInfoBuilder
                .basicInfo(ConfigChangePointCutTypes.PUBLISH_BY_RPC.value(), true, TimeUtils.getCurrentTimeStr())
                .sourceInfo("192.34.67.11", "", "", "")
                .publishOrUpdateInfo("publish-test", "", "", "hello", null, null, null, null, null).build();
        Assert.assertNotNull(configChangeNotifyInfo);
        nacosWebHookPluginService.notifyConfigChange(configChangeNotifyInfo, selfUrl);
        nacosWebHookPluginService.notifyConfigChange(configChangeNotifyInfo, eventUrl);
        try {
            TimeUnit.SECONDS.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
}
