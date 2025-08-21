/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.inner;

import com.alibaba.nacos.console.handler.impl.AbstractServerStateHandler;
import com.alibaba.nacos.core.service.NacosServerStateService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Implementation of ServerStateHandler that performs server state operations.
 *
 * @author zhangyukun
 */
@Service
@EnabledInnerHandler
public class ServerStateInnerHandler extends AbstractServerStateHandler {
    
    private final NacosServerStateService stateService;
    
    public ServerStateInnerHandler(NacosServerStateService stateService) {
        this.stateService = stateService;
    }
    
    public Map<String, String> getServerState() {
        return stateService.getServerState();
    }
}

