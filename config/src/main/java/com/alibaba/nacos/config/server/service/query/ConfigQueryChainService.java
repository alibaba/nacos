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
import com.alibaba.nacos.config.server.service.query.enums.ResponseCode;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service class for initializing and retrieving the configuration query chain builder.
 *
 * @author Nacos
 */
@Service
public class ConfigQueryChainService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigQueryChainService.class);
    
    private final ConfigQueryHandlerChain chain;
    
    public ConfigQueryChainService() {
        String curChain = EnvUtil.getProperty("nacos.config.query.chain.builder", "nacos");
        Optional<ConfigQueryHandlerChainBuilder> optionalBuilder = NacosServiceLoader.load(ConfigQueryHandlerChainBuilder.class)
                .stream()
                .filter(builder -> builder.getName().equals(curChain))
                .findFirst();
        if (optionalBuilder.isPresent()) {
            chain = optionalBuilder.get().build();
            LOGGER.info("ConfigQueryHandlerChain has been initialized successfully with chain: {}", curChain);
        } else {
            String errorMessage = "No suitable ConfigQueryHandlerChainBuilder found for name: " + curChain;
            LOGGER.error(errorMessage);
            throw new NacosConfigException(errorMessage);
        }
    }
    
    /**
     * Handles the configuration query request.
     *
     * @param request the configuration query request object
     * @return the configuration query response object
     */
    public ConfigQueryChainResponse handle(ConfigQueryChainRequest request) {
        try {
            return chain.handle(request);
        } catch (Exception e) {
            LOGGER.error("[Error] Fail to handle ConfigQueryChainRequest", e);
            return ConfigQueryChainResponse.buildFailResponse(ResponseCode.FAIL.getCode(), e.getMessage());
        }
    }
}