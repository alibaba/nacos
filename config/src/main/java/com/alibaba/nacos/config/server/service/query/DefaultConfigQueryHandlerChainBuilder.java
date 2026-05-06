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

import com.alibaba.nacos.config.server.service.query.handler.ConfigChainEntryHandler;
import com.alibaba.nacos.config.server.service.query.handler.ConfigContentTypeHandler;
import com.alibaba.nacos.config.server.service.query.handler.FormalHandler;
import com.alibaba.nacos.config.server.service.query.handler.GrayRuleMatchHandler;
import com.alibaba.nacos.config.server.service.query.handler.SpecialTagNotFoundHandler;

/**
 * DefaultConfigQueryHandlerChainBuilder.
 *
 * @author Nacos
 */
public class DefaultConfigQueryHandlerChainBuilder implements ConfigQueryHandlerChainBuilder {
    
    @Override
    public ConfigQueryHandlerChain build() {
        ConfigQueryHandlerChain chain = new ConfigQueryHandlerChain();
        chain.addHandler(new ConfigChainEntryHandler())
                .addHandler(new ConfigContentTypeHandler())
                .addHandler(new GrayRuleMatchHandler())
                .addHandler(new SpecialTagNotFoundHandler())
                .addHandler(new FormalHandler());
        return chain;
    }
    
    @Override
    public String getName() {
        return "nacos";
    }
}