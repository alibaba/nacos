/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.query;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

/**
 * Service class for initializing and retrieving the configuration query request extractor.
 *
 * @author Nacos
 */
public class ConfigChainRequestExtractorService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChainRequestExtractorService.class);
    
    private static ConfigQueryChainRequestExtractor extractor;
    
    static {
        String curExtractor = EnvUtil.getProperty("nacos.config.query.chain.request.extractor", "nacos");
        Optional<ConfigQueryChainRequestExtractor> optionalBuilder = NacosServiceLoader.load(ConfigQueryChainRequestExtractor.class)
                .stream()
                .filter(builder -> builder.getName().equals(curExtractor))
                .findFirst();
        if (optionalBuilder.isPresent()) {
            extractor = optionalBuilder.get();
            LOGGER.info("ConfigQueryRequestExtractor has been initialized successfully with extractor: {}", curExtractor);
        } else {
            String errorMessage = "No suitable ConfigQueryRequestExtractor found for name: " + curExtractor;
            LOGGER.error(errorMessage);
            throw new NacosConfigException(errorMessage);
        }
    }
    
    public static ConfigQueryChainRequestExtractor getExtractor() {
        return extractor;
    }
}