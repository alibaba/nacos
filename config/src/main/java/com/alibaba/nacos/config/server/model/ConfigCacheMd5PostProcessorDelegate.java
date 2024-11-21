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
public class ConfigCacheMd5PostProcessorDelegate {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCacheFactoryDelegate.class);
    
    private static final ConfigCacheMd5PostProcessorDelegate INSTANCE = new ConfigCacheMd5PostProcessorDelegate();
    
    private String configCacheMd5PostProcessorType = EnvUtil.getProperty("nacos.config.cache.type", "nacos");
    
    private ConfigCacheMd5PostProcessor configCacheMd5PostProcessor;
    
    private ConfigCacheMd5PostProcessorDelegate() {
        Collection<ConfigCacheMd5PostProcessor> processors = NacosServiceLoader.load(ConfigCacheMd5PostProcessor.class);
        for (ConfigCacheMd5PostProcessor processor : processors) {
            if (StringUtils.isEmpty(processor.getPostProcessorName())) {
                LOGGER.warn(
                        "[ConfigCacheMd5PostProcessor] Load ConfigCacheMd5PostProcessor({}) PostProcessorName(null/empty) fail. "
                                + "Please add PostProcessorName to resolve",
                        processor.getClass().getName());
                continue;
            }
            if (StringUtils.equals(configCacheMd5PostProcessorType, processor.getPostProcessorName())) {
                this.configCacheMd5PostProcessor = processor;
            }
        }
        if (configCacheMd5PostProcessor == null) {
            configCacheMd5PostProcessor = new NacosConfigCacheMd5PostProcessor();
        }
    }
    
    public static ConfigCacheMd5PostProcessorDelegate getInstance() {
        return INSTANCE;
    }
    
    public void postProcess(ConfigCache configCache, String content) {
        configCacheMd5PostProcessor.postProcess(configCache, content);
    }
}
