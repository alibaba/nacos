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

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.config.server.service.notify.HttpClientManager;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeType;
import com.alibaba.nacos.plugin.config.impl.webhook.WebHookCloudEventStrategy;
import com.alibaba.nacos.plugin.config.model.ConfigChangeNotifyInfo;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * tests for webhook notify.
 *
 * @author liyunfei
 */
public class WebhookNotifyStrategyTests {
    
    @Before
    public void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED, "true");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_WAY, "nacos");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL,
                "http://**");
        // optional if webhook_url is not empty,will push to url;if not will push the endpoint
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ACCESS_KEY_ID,
                "**");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ACCESS_KEY_SECRET,
                "**");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENDPOINT,
                "**");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_EVENT_BUS, "**");
        env.setProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_SOURCE, "**");
        
        env.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED, "true");
        env.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_WAY, "nacos");
        env.setProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_URLS, "");
        env.setProperty(ConfigChangeConstants.FileFormatCheck.NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_ENABLED, "true");
        
        EnvUtil.setEnvironment(env);
    }
    
    @Test
    public void testEventCloudNotify() {
        ConfigChangeNotifyInfo configChangeNotifyInfo = new ConfigChangeNotifyInfo("publish",
                System.currentTimeMillis(), "publish-test.text", "DEFAULT_GROUP");
        configChangeNotifyInfo.setRequestIp("127.0.0.1");
        Map<String, String> contentItem = new HashMap<>();
        contentItem.put("newValue", "test,publish");
        configChangeNotifyInfo.setContentItem(contentItem);
        WebHookCloudEventStrategy webHookCloudEventStrategy = new WebHookCloudEventStrategy();
        // push to default endpoint
        webHookCloudEventStrategy.notifyConfigChange(configChangeNotifyInfo, null);
        // push to the point url
        webHookCloudEventStrategy.notifyConfigChange(configChangeNotifyInfo, "http://localhost:8080/webhook/send");
        // keep pool work
        try {
            TimeUnit.SECONDS.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
}
