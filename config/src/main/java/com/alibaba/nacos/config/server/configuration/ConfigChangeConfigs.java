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

package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * config change plugin configs.
 *
 * @author liyunfei
 **/
@Configuration
public class ConfigChangeConfigs extends Subscriber<ServerConfigChangeEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangeConfigs.class);

    private static final String PREFIX = ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX;

    /**
     * Whether webhook config change plugin enabled.
     */
    @Value("${" + ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED + ":false}")
    private boolean webhookEnabled;

    /**
     * The url which webhook.
     */
    @Value("${" + ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL + ":}")
    private String webhookUrl;

    /**
     * The max content capacity which webhook push.
     */
    @Value("${" + ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_CONTENT_MAX_CAPACITY + ":102400}")
    private int webhookContentMaxCapacity;

    /**
     * Whether whitelist config change plugin enabled.
     */
    @Value("${" + ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED + ":false}")
    private boolean whiteListEnabled;

    /**
     * Whether whitelist sets.
     */
    @Value("${" + ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_SUFFIXS + ":}")
    private String whiteListSuffixs;

    /**
     * Whether file format config change plugin enabled.
     */
    @Value("${" + ConfigChangeConstants.FileFormatCheck.NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_ENABLED + ":false}")
    private boolean fileFormatCheckEnabled;

    private Map<String, Properties> configPluginProperties = new HashMap<>();

    public ConfigChangeConfigs() {
        NotifyCenter.registerSubscriber(this);
        refreshPluginProperties();
    }

    public boolean isWebHookEnabled() {
        return webhookEnabled;
    }

    public String getWebHookUrl() {
        return webhookUrl;
    }

    public int getWebHookMaxContentCapacity() {
        // default 100kb
        return webhookContentMaxCapacity;
    }

    public boolean isWhiteListEnabled() {
        return whiteListEnabled;
    }

    public String getWhiteListSuffixs() {
        return whiteListSuffixs;
    }

    public boolean isFileFormatCheckEnabled() {
        return whiteListEnabled;
    }

    private void refreshPluginProperties() {
        try {
            Map<String, Properties> newProperties = new HashMap<>(3);
            Properties properties = PropertiesUtil.getPropertiesWithPrefix(EnvUtil.getEnvironment(), PREFIX);
            for (String each : properties.stringPropertyNames()) {
                int typeIndex = each.indexOf('.');
                String type = each.substring(0, typeIndex);
                String subKey = each.substring(typeIndex + 1);
                newProperties.computeIfAbsent(type, key -> new Properties())
                        .setProperty(subKey, properties.getProperty(each));
            }
            configPluginProperties = newProperties;
        } catch (Exception e) {
            LOGGER.warn("[ConfigChangeConfigs]Refresh config plugin properties failed ", e);
        }
    }

    public Properties getPluginProperties(String configPluginType) {
        if (!configPluginProperties.containsKey(configPluginType)) {
            LOGGER.warn("[ConfigChangeConfigs]Can't find config plugin properties for type {}, will use empty properties", configPluginType);
            return new Properties();
        }
        return configPluginProperties.get(configPluginType);
    }

    @Override
    public void onEvent(ServerConfigChangeEvent event) {
        try {
            webhookEnabled = EnvUtil.getProperty(ConfigChangeConstants.Webhook
                    .NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED, Boolean.class);
            webhookUrl = EnvUtil.getProperty(ConfigChangeConstants.Webhook
                    .NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL, String.class);
            webhookContentMaxCapacity = EnvUtil.getProperty(ConfigChangeConstants.Webhook
                    .NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_CONTENT_MAX_CAPACITY, Integer.class);
            whiteListEnabled = EnvUtil.getProperty(ConfigChangeConstants.WhiteList
                    .NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED, Boolean.class);
            whiteListSuffixs = EnvUtil.getProperty(ConfigChangeConstants.WhiteList
                    .NACOS_CORE_CONFIG_PLUGIN_WHITELIST_SUFFIXS, String.class);
            fileFormatCheckEnabled = EnvUtil.getProperty(ConfigChangeConstants.FileFormatCheck
                    .NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_ENABLED, Boolean.class);
            LOGGER.info("[ConfigChangeConfigs]Upgrade config change plugin configs successfully, {} enabled:{} , {} enabled:{}, {} enbaled: {}",
                    "webhook", isWebHookEnabled(), "fileformatcheck", isFileFormatCheckEnabled(), "whitelist", isWhiteListEnabled());
        } catch (Exception e) {
            LOGGER.warn("[ConfigChangeConfigs]Upgrade config change plugin config from env failed, use old value", e);
        }
    }

    @Override
    public Class<? extends Event> subscribeType() {
        return ServerConfigChangeEvent.class;
    }
}
