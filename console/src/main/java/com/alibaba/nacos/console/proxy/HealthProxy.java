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
 *
 */

package com.alibaba.nacos.console.proxy;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.console.config.ConsoleConfig;
import com.alibaba.nacos.console.handler.HealthHandler;
import com.alibaba.nacos.console.handler.inner.HealthInnerHandler;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Proxy class for handling health check operations.
 *
 * @author zhangyukun
 */
@Service
public class HealthProxy {
    
    private final Map<String, HealthHandler> healthHandlerMap = new HashMap<>();
    
    private final ConsoleConfig consoleConfig;
    
    public HealthProxy(HealthInnerHandler healthInnerHandler, ConsoleConfig consoleConfig) {
        this.healthHandlerMap.put("merged", healthInnerHandler);
        this.consoleConfig = consoleConfig;
    }
    
    /**
     * Perform readiness check to determine if Nacos is ready to handle requests.
     *
     * @return readiness result
     */
    public Result<String> checkReadiness() throws NacosException {
        HealthHandler healthHandler = healthHandlerMap.get(consoleConfig.getType());
        if (healthHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return healthHandler.checkReadiness();
    }
}

