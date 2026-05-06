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
            if (StringUtils.isEmpty(each.getName())) {
                LOGGER.warn(
                        "[ConfigCacheFactoryDelegate] Load ConfigCacheFactory({}) ConfigFactroyName (null/empty) fail. "
                                + "Please add ConfigFactoryName to resolve", each.getClass().getName());
                continue;
            }
            LOGGER.info(
                    "[ConfigCacheFactoryDelegate] Load ConfigCacheFactory({}) ConfigCacheFactoryName({}) successfully. ",
                    each.getClass().getName(), each.getName());
            if (StringUtils.equals(configCacheFactoryType, each.getName())) {
                LOGGER.info("[ConfigCacheFactoryDelegate] Matched ConfigCacheFactory found,set configCacheFactory={}",
                        each.getClass().getName());
                this.configCacheFactory = each;
            }
        }
        if (this.configCacheFactory == null) {
            LOGGER.info(
                    "[ConfigCacheFactoryDelegate] Matched ConfigCacheFactory not found, Load Default NacosConfigCacheFactory successfully.");
            this.configCacheFactory = new NacosConfigCacheFactory();
        }
    }
    
    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static ConfigCacheFactoryDelegate getInstance() {
        return INSTANCE;
    }
    
    /**
     * Create config cache config cache.
     *
     * @return the config cache
     */
    public ConfigCache createConfigCache() {
        return configCacheFactory.createConfigCache();
    }
    
    /**
     * Create config cache config cache.
     *
     * @param md5            the md 5
     * @param lastModifiedTs the last modified ts
     * @return the config cache
     */
    public ConfigCache createConfigCache(String md5, long lastModifiedTs) {
        ConfigCache configCache = this.createConfigCache();
        configCache.setMd5(md5);
        configCache.setLastModifiedTs(lastModifiedTs);
        return configCache;
    }
    
    /**
     * Create config cache gray config cache gray.
     *
     * @return the config cache gray
     */
    public ConfigCacheGray createConfigCacheGray() {
        return configCacheFactory.createConfigCacheGray();
    }
    
    /**
     * Create config cache gray config cache gray.
     *
     * @param grayName the gray name
     * @return the config cache gray
     */
    public ConfigCacheGray createConfigCacheGray(String grayName) {
        ConfigCacheGray configCacheGray = configCacheFactory.createConfigCacheGray();
        configCacheGray.setGrayName(grayName);
        return configCacheGray;
    }
}
