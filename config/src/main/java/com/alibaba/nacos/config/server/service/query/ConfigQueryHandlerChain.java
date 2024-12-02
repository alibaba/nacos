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

import com.alibaba.nacos.config.server.service.query.handler.ConfigQueryHandler;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * ConfigQueryHandlerChain.
 * @author Nacos
 */
public class ConfigQueryHandlerChain {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigQueryHandlerChain.class);
    
    private ConfigQueryHandler head;
    
    private ConfigQueryHandler tail;
    
    public ConfigQueryHandlerChain() {
    }
    
    /**
     * Adds a new configuration query handler to the chain.
     *
     * @param handler the configuration query handler to be added
     * @return the current configuration query handler chain object, supporting method chaining
     */
    public ConfigQueryHandlerChain addHandler(ConfigQueryHandler handler) {
        if (Objects.isNull(handler)) {
            LOGGER.warn("Attempted to add a null config query handler");
            return this;
        }
        
        if (head == null) {
            head = handler;
            tail = handler;
        } else {
            tail.setNextHandler(handler);
            tail = handler;
        }
        
        return this;
    }
    
    public ConfigQueryChainResponse handle(ConfigQueryChainRequest request) throws IOException {
        return head.handle(request);
    }
    
}