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

import com.alibaba.nacos.config.server.remote.query.handler.ConfigQueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * DefaultConfigQueryHandlerChainBuilder.
 * @author Nacos
 */
public class DefaultConfigQueryHandlerChainBuilder implements ConfigQueryHandlerChainBuilder {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConfigQueryHandlerChainBuilder.class);
    
    private ConfigQueryHandler head;
    
    private ConfigQueryHandler tail;
    
    @Override
    public ConfigQueryHandlerChain build() {
        return new ConfigQueryHandlerChain(head);
    }
    
    @Override
    public ConfigQueryHandlerChainBuilder addHandler(ConfigQueryHandler handler) {
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
}