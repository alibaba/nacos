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
 * The type Config cache md5 post processor delegate.
 *
 * @author Sunrisea
 */
public class ConfigCachePostProcessorDelegate {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCacheFactoryDelegate.class);
    
    private static final ConfigCachePostProcessorDelegate INSTANCE = new ConfigCachePostProcessorDelegate();
    
    private String configCacheMd5PostProcessorType = EnvUtil.getProperty("nacos.config.cache.type", "nacos");
    
    private ConfigCachePostProcessor configCachePostProcessor;
    
    private ConfigCachePostProcessorDelegate() {
        Collection<ConfigCachePostProcessor> processors = NacosServiceLoader.load(ConfigCachePostProcessor.class);
        for (ConfigCachePostProcessor processor : processors) {
            if (StringUtils.isEmpty(processor.getName())) {
                LOGGER.warn(
                        "[ConfigCachePostProcessorDelegate] Load ConfigCachePostProcessor({}) PostProcessorName(null/empty) fail. "
                                + "Please add PostProcessorName to resolve", processor.getClass().getName());
                continue;
            }
            LOGGER.info(
                    "[ConfigCachePostProcessorDelegate] Load ConfigCachePostProcessor({}) PostProcessorName({}) successfully. ",
                    processor.getClass().getName(), processor.getName());
            if (StringUtils.equals(configCacheMd5PostProcessorType, processor.getName())) {
                LOGGER.info(
                        "[ConfigCachePostProcessorDelegate] Matched ConfigCachePostProcessor found,set configCacheFactory={}",
                        processor.getClass().getName());
                this.configCachePostProcessor = processor;
            }
        }
        if (configCachePostProcessor == null) {
            LOGGER.info(
                    "[ConfigCachePostProcessorDelegate] Matched ConfigCachePostProcessor not found, "
                            + "load Default NacosConfigCachePostProcessor successfully");
            configCachePostProcessor = new NacosConfigCachePostProcessor();
        }
    }
    
    public static ConfigCachePostProcessorDelegate getInstance() {
        return INSTANCE;
    }
    
    public void postProcess(ConfigCache configCache, String content) {
        configCachePostProcessor.postProcess(configCache, content);
    }
}
