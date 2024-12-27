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

package com.alibaba.nacos.console.proxy.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.console.config.ConsoleConfig;
import com.alibaba.nacos.console.handler.config.HistoryHandler;
import com.alibaba.nacos.console.handler.inner.config.HistoryInnerHandler;
import com.alibaba.nacos.persistence.model.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * .
 *
 * @author zhangyukun on:2024/8/16
 */
@Service
public class HistoryProxy {
    
    private final Map<String, HistoryHandler> historyHandlerMap = new HashMap<>();
    
    private final ConsoleConfig consoleConfig;
    
    /**
     * Constructs a new HistoryProxy with the given HistoryInnerHandler and ConsoleConfig.
     *
     * @param historyInnerHandler the default implementation of HistoryHandler
     * @param consoleConfig       the console configuration used to determine the deployment type
     */
    @Autowired
    public HistoryProxy(HistoryInnerHandler historyInnerHandler, ConsoleConfig consoleConfig) {
        this.historyHandlerMap.put("merged", historyInnerHandler);
        this.consoleConfig = consoleConfig;
    }
    
    /**
     * Query the detailed configuration history information.
     *
     * @param dataId      the ID of the data
     * @param group       the group ID
     * @param namespaceId the namespace ID
     * @param nid         the history record ID
     * @return the detailed configuration history information
     * @throws NacosException if any error occurs during the operation
     */
    public ConfigHistoryInfo getConfigHistoryInfo(String dataId, String group, String namespaceId, Long nid)
            throws NacosException {
        HistoryHandler historyHandler = historyHandlerMap.get(consoleConfig.getType());
        if (historyHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return historyHandler.getConfigHistoryInfo(dataId, group, namespaceId, nid);
    }
    
    /**
     * Query the list of configuration history.
     *
     * @param dataId      the ID of the data
     * @param group       the group ID
     * @param namespaceId the namespace ID
     * @param pageNo      the page number
     * @param pageSize    the number of items per page
     * @return the paginated list of configuration history
     * @throws NacosException if any error occurs during the operation
     */
    public Page<ConfigHistoryInfo> listConfigHistory(String dataId, String group, String namespaceId, Integer pageNo,
            Integer pageSize) throws NacosException {
        HistoryHandler historyHandler = historyHandlerMap.get(consoleConfig.getType());
        if (historyHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return historyHandler.listConfigHistory(dataId, group, namespaceId, pageNo, pageSize);
    }
    
    /**
     * Query the previous configuration history information.
     *
     * @param dataId      the ID of the data
     * @param group       the group ID
     * @param namespaceId the namespace ID
     * @param id          the configuration ID
     * @return the previous configuration history information
     * @throws NacosException if any error occurs during the operation
     */
    public ConfigHistoryInfo getPreviousConfigHistoryInfo(String dataId, String group, String namespaceId, Long id)
            throws NacosException {
        HistoryHandler historyHandler = historyHandlerMap.get(consoleConfig.getType());
        if (historyHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return historyHandler.getPreviousConfigHistoryInfo(dataId, group, namespaceId, id);
    }
    
    /**
     * Query the list of configurations by namespace.
     *
     * @param namespaceId the namespace ID
     * @return the list of configurations
     * @throws NacosApiException if any error occurs during the operation
     */
    public List<ConfigInfoWrapper> getConfigsByTenant(String namespaceId) throws NacosException {
        HistoryHandler historyHandler = historyHandlerMap.get(consoleConfig.getType());
        if (historyHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return historyHandler.getConfigsByTenant(namespaceId);
    }
}
