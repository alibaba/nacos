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

import com.alibaba.nacos.config.server.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.remote.query.handler.ConfigQueryHandler;

import java.io.IOException;

/**
 * ConfigQueryHandlerChain.
 * @author Nacos
 */
public class ConfigQueryHandlerChain {
    
    private final ConfigQueryHandler headHandler;
    
    public ConfigQueryHandlerChain(ConfigQueryHandler headHandler) {
        this.headHandler = headHandler;
    }
    
    public ConfigQueryChainResponse handle(ConfigQueryChainRequest request) throws IOException {
        return headHandler.handle(request);
    }
}