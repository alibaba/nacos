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

package com.alibaba.nacos.plugin.config.constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * all configuration changes plugin contansts.
 *
 * @author liyunfei
 */
public class ConfigChangeConstants {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangeConstants.class);
    
    private static Map<String, String> pointcutsMap = new ConcurrentHashMap<>();
    
    public static final String ASYNC_TYPE = "async";
    
    public static final String SYNC_TYPE = "sync";
    
    public static final String NACOS_IMPL_WAY = "nacos";
    
    public static final String CLOUD_EVENT_WEBHOOK_NOTIFY_TYPE = "cloudevent";
    
    public static final String NACOS_CORE_CONFIG_PLUGIN_PREFIX = "nacos.core.config.plugin.";
    
    public static class Webhook {
        
        public static final ConfigChangeType[] CONFIG_CHANGE_TYPES = {ConfigChangeType.IMPORT,
                ConfigChangeType.PUBLISH,ConfigChangeType.REMOVE,ConfigChangeType.UPDATE,ConfigChangeType.BATCH_REMOVE};
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_POINTCUT_NAMES = "import,publish,remove,update";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_PREFIX = "nacos.core.config.plugin.webhook";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENABLED = "nacos.core.config.plugin.webhook.enabled";
        
        /**
         * take which way to implements the WebHookService (Default,UserSelfDefine).
         */
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_WAY = "nacos.core.config.plugin.webhook.way";
        
        /**
         * take which type of webhook ding talk,wechat,lark and so on.
         */
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_TYPE = "nacos.core.config.plugin.webhook.type";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_URL = "nacos.core.config.plugin.webhook.url";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_MAX_CONTENT_CAPACITY = "nacos.core.config.plugin.webhook.maxContentCapacity";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ACCESS_KEY_ID = "nacos.core.config.plugin.webhook.accessKeyId";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ACCESS_KEY_SECRET = "nacos.core.config.plugin.webhook.accessKeySecret";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_ENDPOINT = "nacos.core.config.plugin.webhook.endpoint";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_EVENT_BUS = "nacos.core.config.plugin.webhook.eventbus";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WEBHOOK_SOURCE = "nacos.core.config.plugin.webhook.source";
        
    }
    
    public static class FileFormatCheck {
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_POINTCUT_NAMES = "publish";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_PREFIX = "nacos.core.config.plugin.fileformatcheck";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_ENABLED = "nacos.core.config.plugin.fileformatcheck.enabled";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_FILEFORMATCHECK_WAY = "nacos.core.config.plugin.fileformatcheck.way";
        
    }
    
    public static class WhiteList {
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_POINTCUT_NAMES = "import";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WHITELIST_PREFIX = "nacos.core.config.plugin.whitelist";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WHITELIST_ENABLED = "nacos.core.config.plugin.whitelist.enabled";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WHITELIST_WAY = "nacos.core.config.plugin.whitelist.way";
        
        public static final String NACOS_CORE_CONFIG_PLUGIN_WHITELIST_URLS = "nacos.core.config.plugin.whitelist.urls";
        
    }
    
    // Please Add Respond config here.
    
    static {
        Class[] innerClasses = ConfigChangeConstants.class.getDeclaredClasses();
        for (Class clazz : innerClasses) {
            try {
                pointcutsMap.put(clazz.getSimpleName().toLowerCase(Locale.ROOT),
                        (String) clazz.getField("NACOS_CORE_CONFIG_PLUGIN_POINTCUT_NAMES").get(null));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                LOGGER.error("[{}] Load Config Change,Please NACOS_CORE_CONFIG_PLUGIN_POINTCUT_NAMES At {} ",
                        ConfigChangeConstants.class, clazz);
            }
        }
    }
    
    public static String getPointcuts(String serviceType) {
        return pointcutsMap.get(serviceType);
    }
}

