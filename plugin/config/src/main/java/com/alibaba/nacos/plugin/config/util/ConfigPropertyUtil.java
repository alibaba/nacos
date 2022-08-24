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

package com.alibaba.nacos.plugin.config.util;

import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * require the config change plugin sys config info(webhook,filecheck,whitelist).
 *
 * @author liyunfei
 */
public class ConfigPropertyUtil {
    
    public static boolean getWebHookEnabled() {
        Object enabled = EnvUtil
                .getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED, Boolean.class);
        if (enabled == null) {
            return false;
        }
        return (boolean) enabled;
    }
    
    public static String getWebHookUrl() {
        return EnvUtil.getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL, String.class);
    }
    
    public static String getWebHookWay() {
        return EnvUtil.getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_WAY, String.class);
    }
    
    public static String getWebHookType() {
        return EnvUtil.getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_TYPE, String.class);
    }
    
    public static String getWebHookAccessKeyId() {
        return EnvUtil
                .getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ACCESS_KEY_ID, String.class);
    }
    
    public static String getWebHookAccessKeySecret() {
        return EnvUtil
                .getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ACCESS_KEY_SECRET, String.class);
    }
    
    public static String getWebHookEndpoint() {
        return EnvUtil
                .getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENDPOINT, String.class);
    }
    
    public static String getWebHookEventBus() {
        return EnvUtil
                .getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_EVENT_BUS, String.class);
    }
    
    public static String getWebHookSource() {
        return EnvUtil
                .getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_SOURCE, String.class);
    }
    
    public static boolean getWhiteListEnabled() {
        Object enabled = EnvUtil
                .getProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED, Boolean.class);
        if (enabled == null) {
            return false;
        }
        return (boolean) enabled;
    }
    
    public static String getWhiteListWay() {
        return EnvUtil
                .getProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_WAY, String.class);
    }
    
    public static String getWhiteListUrls() {
        return EnvUtil
                .getProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_URLS, String.class);
    }
    
    public static boolean getFileCheckEnabled() {
        Object enabled = EnvUtil
                .getProperty(ConfigChangeConstants.FileFormatCheck.NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_ENABLED,
                        Boolean.class);
        if (enabled == null) {
            return false;
        }
        return (boolean) enabled;
    }
    
    public static String getFileCheckWay() {
        return EnvUtil.getProperty(ConfigChangeConstants.FileFormatCheck.NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_WAY,
                String.class);
    }
}
