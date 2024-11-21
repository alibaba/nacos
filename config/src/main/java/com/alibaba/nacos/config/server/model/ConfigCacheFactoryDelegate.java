/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * The type Config cache factory delegate.
 *
 * @author Sunrisea
 */
public class ConfigCacheFactoryDelegate {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCacheFactoryDelegate.class);
    
    private static final ConfigCacheFactoryDelegate INSTANCE = new ConfigCacheFactoryDelegate();
    
    private String configCacheFactoryType = EnvUtil.getProperty("nacos.config.cache.type", "nacos");
    
    private ConfigCacheFactory configCacheFactory = null;
    
    private ConfigCacheFactoryDelegate() {
        Collection<ConfigCacheFactory> configCacheFactories = NacosServiceLoader.load(ConfigCacheFactory.class);
        for (ConfigCacheFactory each : configCacheFactories) {
            if (StringUtils.isEmpty(each.getConfigCacheFactoryName())) {
                LOGGER.warn("[ConfigCacheFactory] Load ConfigCacheFactory({}) ConfigFactroyName (null/empty) fail. "
                                + "Please add ConfigFactoryName to resolve",
                        each.getClass());
                continue;
            }
            LOGGER.info("[ConfigCacheFactory] Load ConfigCacheFactory({}) ConfigCacheFactoryName({}) successfully. ",
                    each.getClass(), each.getConfigCacheFactoryName());
            if (StringUtils.equals(configCacheFactoryType, each.getConfigCacheFactoryName())) {
                this.configCacheFactory = each;
            }
        }
        if (this.configCacheFactory == null) {
            this.configCacheFactory = new NacosConfigCacheFactory();
        }
    }
    
    public static ConfigCacheFactoryDelegate getInstance() {
        return INSTANCE;
    }
    
    public ConfigCache createConfigCache() {
        return configCacheFactory.createConfigCache();
    }
    
    public ConfigCache createConfigCache(String md5, long lastModifiedTs) {
        return configCacheFactory.createConfigCache(md5, lastModifiedTs);
    }
    
    public ConfigCacheGray createConfigCacheGray(String grayName) {
        return configCacheFactory.createConfigCacheGray(grayName);
    }
    
    public ConfigCacheGray createConfigCacheGray(String md5, long lastModifiedTs, String grayRule) {
        return configCacheFactory.createConfigCacheGray(md5, lastModifiedTs, grayRule);
    }
}
