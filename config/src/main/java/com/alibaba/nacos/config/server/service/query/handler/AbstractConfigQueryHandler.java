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

package com.alibaba.nacos.config.server.service.query.handler;

/**
 * AbstractConfigQueryHandler. This abstract class provides a base implementation for configuration query handlers. It
 * implements the {@link ConfigQueryHandler} interface and handles the chaining of handlers.
 *
 * @author Nacos
 */
public abstract class AbstractConfigQueryHandler implements ConfigQueryHandler {
    
    public ConfigQueryHandler nextHandler;
    
    @Override
    public ConfigQueryHandler getNextHandler() {
        return this.nextHandler;
    }
    
    @Override
    public void setNextHandler(ConfigQueryHandler nextHandler) {
        this.nextHandler = nextHandler;
    }
    
}