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
 * ConfigPropertyUtil.
 *
 * @author liyunfei
 */
public class ConfigPropertyUtil {
    
    public static boolean getWebHookEnabled() {
        Boolean enabled = EnvUtil
                .getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED, Boolean.class);
        return enabled != null && enabled;
    }
    
    public static String getWebHookUrl() {
        return EnvUtil.getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL, String.class);
    }
    
    public static int getWebHookMaxContentCapacity() {
        Integer maxContentCapacity = EnvUtil
                .getProperty(ConfigChangeConstants.Webhook.NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_MAX_CONTENT_CAPACITY,
                        Integer.class);
        // default 100kb
        return maxContentCapacity == null ? 100 * 1024 : maxContentCapacity;
    }
    
    public static boolean getWhiteListEnabled() {
        Boolean enabled = EnvUtil
                .getProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED, Boolean.class);
        return enabled != null && enabled;
    }
    
    public static String getWhiteListUrls() {
        return EnvUtil
                .getProperty(ConfigChangeConstants.WhiteList.NACOS_CORE_CONFIG_PLUGIN_WHITELIST_URLS, String.class);
    }
    
    public static boolean getFileCheckEnabled() {
        Boolean enabled = EnvUtil
                .getProperty(ConfigChangeConstants.FileFormatCheck.NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_ENABLED,
                        Boolean.class);
        return enabled != null && enabled;
    }
    
}
