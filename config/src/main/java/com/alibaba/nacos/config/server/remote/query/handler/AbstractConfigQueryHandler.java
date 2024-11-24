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

package com.alibaba.nacos.config.server.remote.query.handler;

import com.alibaba.nacos.config.server.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.model.ConfigQueryChainResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * AbstractConfigQueryHandler.
 * This abstract class provides a base implementation for configuration query handlers.
 * It implements the {@link ConfigQueryHandler} interface and handles the chaining of handlers.
 * Subclasses must implement the {@code canHandler} and {@code doHandle} methods to define specific handling logic.
 *
 * @author Nacos
 */
public abstract class AbstractConfigQueryHandler implements ConfigQueryHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractConfigQueryHandler.class);
    
    public ConfigQueryHandler nextHandler;
    
    @Override
    public ConfigQueryChainResponse handle(ConfigQueryChainRequest request) throws IOException {
        if (canHandler(request)) {
            return doHandle(request);
        } else if (nextHandler != null) {
            return nextHandler.handle(request);
        } else {
            LOGGER.warn("No handler can process the request: {}", request);
            return new ConfigQueryChainResponse();
        }
    }
    
    /**
     * Determines if this handler can process the given request.
     * Subclasses must implement this method to define their specific conditions.
     *
     * @param request The configuration query request.
     * @return True if this handler can process the request; otherwise, false.
     */
    public abstract boolean canHandler(ConfigQueryChainRequest request);
    
    /**
     * Processes the given request.
     * This method is called only if {@code canHandler} returns true.
     * Subclasses must implement this method to define the actual handling logic.
     *
     * @param request The configuration query request.
     * @return The response to the configuration query.
     * @throws IOException If an I/O error occurs.
     */
    public abstract ConfigQueryChainResponse doHandle(ConfigQueryChainRequest request) throws IOException;
    
    public void setNextHandler(ConfigQueryHandler nextHandler) {
        this.nextHandler = nextHandler;
    }
    
    public ConfigQueryHandler getNextHandler() {
        return this.nextHandler;
    }
    
}