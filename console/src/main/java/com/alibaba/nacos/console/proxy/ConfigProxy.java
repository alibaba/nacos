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
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.console.config.ConsoleConfig;
import com.alibaba.nacos.console.handler.ConfigHandler;
import com.alibaba.nacos.console.handler.inner.ConfigInnerHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Proxy class for handling configuration operations.
 *
 * @author zhangyukun
 */
@Service
public class ConfigProxy {
    
    private final Map<String, ConfigHandler> configHandlerMap = new HashMap<>();
    
    private final ConsoleConfig consoleConfig;
    
    @Autowired
    public ConfigProxy(ConfigInnerHandler configInnerHandler, ConsoleConfig consoleConfig) {
        //        this.configHandlerMap.put("separate", configInnerHandler);
        this.configHandlerMap.put("merged", configInnerHandler);
        this.consoleConfig = consoleConfig;
    }
    
    /**
     * Fetches the configuration.
     *
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     * @throws NacosException   if a Nacos error occurs
     */
    public void getConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String tag) throws IOException, ServletException, NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        configHandler.getConfig(request, response, dataId, group, tenant, tag);
    }
    
    /**
     * Publishes the configuration.
     *
     * @throws NacosException if a Nacos error occurs
     */
    public boolean publishConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String content, String tag, String appName, String srcUser, String configTags, String desc,
            String use, String effect, String type, String schema, String encryptedDataKey) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.publishConfig(request, response, dataId, group, tenant, content, tag, appName, srcUser,
                configTags, desc, use, effect, type, schema, encryptedDataKey);
    }
    
    /**
     * Deletes the configuration.
     *
     * @throws NacosException if a Nacos error occurs
     */
    public boolean deleteConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String tag) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.deleteConfig(request, response, dataId, group, tenant, tag);
    }
    
    /**
     * Gets detailed configuration information.
     *
     * @throws NacosException if a Nacos error occurs
     */
    public ConfigAllInfo detailConfigInfo(String dataId, String group, String tenant) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.detailConfigInfo(dataId, group, tenant);
    }
}
