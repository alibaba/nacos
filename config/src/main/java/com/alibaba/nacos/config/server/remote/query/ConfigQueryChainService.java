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

package com.alibaba.nacos.config.server.remote.query;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
import com.alibaba.nacos.config.server.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.remote.query.handler.ConfigChainEntryHandler;
import com.alibaba.nacos.config.server.remote.query.handler.FormalHandler;
import com.alibaba.nacos.config.server.remote.query.handler.GrayRuleMatchHandler;
import com.alibaba.nacos.config.server.remote.query.handler.TagNotFoundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * ConfigQueryChainService.
 * @author Nacos
 */
@Service
public class ConfigQueryChainService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigQueryChainService.class);
    
    private final ConfigQueryHandlerChain configQueryHandlerChain;
    
    public ConfigQueryChainService() {
        Collection<ConfigQueryHandlerChainBuilder> configQueryHandlerChainBuilders =
                NacosServiceLoader.load(ConfigQueryHandlerChainBuilder.class);
        if (CollectionUtils.isEmpty(configQueryHandlerChainBuilders)) {
            throw new NacosConfigException("No ConfigQueryHandlerChainBuilder found");
        }
        ConfigQueryHandlerChainBuilder builder = configQueryHandlerChainBuilders.iterator().next();
        configQueryHandlerChain = builder.addHandler(new ConfigChainEntryHandler())
                .addHandler(new GrayRuleMatchHandler())
                .addHandler(new TagNotFoundHandler())
                .addHandler(new FormalHandler())
                .build();
    }
    
    /**
     * Handles the configuration query request.
     *
     * @param request the configuration query request object
     * @return the configuration query response object
     */
    public ConfigQueryChainResponse handle(ConfigQueryChainRequest request) {
        try {
            return configQueryHandlerChain.handle(request);
        } catch (Exception e) {
            LOGGER.error("[Error] Fail to handle ConfigQueryChainRequest", e);
            return new ConfigQueryChainResponse();
        }
    }
}