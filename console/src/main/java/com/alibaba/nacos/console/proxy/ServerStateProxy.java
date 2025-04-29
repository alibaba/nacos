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
import com.alibaba.nacos.console.handler.ServerStateHandler;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Proxy class for handling server state operations.
 *
 * @author zhangyukun
 */
@Service
public class ServerStateProxy {
    
    private final ServerStateHandler serverStateHandler;
    
    public ServerStateProxy(ServerStateHandler serverStateHandler) {
        this.serverStateHandler = serverStateHandler;
    }
    
    /**
     * Get the current state of the server.
     *
     * @return the server state as a Map
     */
    public Map<String, String> getServerState() throws NacosException {
        return serverStateHandler.getServerState();
    }
    
    /**
     * Get the announcement content based on the language.
     *
     * @param language the language for the announcement
     * @return the announcement content as a String
     */
    public String getAnnouncement(String language) {
        return serverStateHandler.getAnnouncement(language);
    }
    
    /**
     * Get the console UI guide information.
     *
     * @return the console UI guide information as a String
     */
    public String getConsoleUiGuide() {
        return serverStateHandler.getConsoleUiGuide();
    }
}

