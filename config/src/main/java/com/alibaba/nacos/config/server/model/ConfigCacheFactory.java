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

/**
 * The interface Config cache factory.
 *
 * @author Sunrisea
 */
public interface ConfigCacheFactory {
    
    /**
     * Create config cache config cache.
     *
     * @return the config cache
     */
    public ConfigCache createConfigCache();
    
    /**
     * Create config cache config cache.
     *
     * @param md5            the md 5
     * @param lastModifiedTs the last modified ts
     * @return the config cache
     */
    public ConfigCache createConfigCache(String md5, long lastModifiedTs);
    
    /**
     * Create config cache gray config cache gray.
     *
     * @param grayName the gray name
     * @return the config cache gray
     */
    public ConfigCacheGray createConfigCacheGray(String grayName);
    
    /**
     * Create config cache gray config cache gray.
     *
     * @param md5            the md 5
     * @param lastModifiedTs the last modified ts
     * @param grayRule       the gray rule
     * @return the config cache gray
     */
    public ConfigCacheGray createConfigCacheGray(String md5, long lastModifiedTs, String grayRule);
    
    /**
     * Gets config cache factroy name.
     *
     * @return the config cache factory name
     */
    public String getConfigCacheFactoryName();
}
