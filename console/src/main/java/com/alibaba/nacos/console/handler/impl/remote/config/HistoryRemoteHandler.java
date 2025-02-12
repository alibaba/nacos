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

package com.alibaba.nacos.console.handler.impl.remote.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.console.handler.config.HistoryHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.api.model.Page;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Remote Implementation of HistoryHandler for handling internal configuration operations.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class HistoryRemoteHandler implements HistoryHandler {
    
    public HistoryRemoteHandler() {
    }
    
    @Override
    public ConfigHistoryInfo getConfigHistoryInfo(String dataId, String group, String namespaceId, Long nid)
            throws NacosException {
        // TODO get from nacos servers
        return new ConfigHistoryInfo();
    }
    
    @Override
    public Page<ConfigHistoryInfo> listConfigHistory(String dataId, String group, String namespaceId, Integer pageNo,
            Integer pageSize) throws NacosException {
        // TODO get from nacos servers
        return new Page<>();
    }
    
    @Override
    public ConfigHistoryInfo getPreviousConfigHistoryInfo(String dataId, String group, String namespaceId, Long id)
            throws NacosException {
        // TODO get from nacos servers
        return new ConfigHistoryInfo();
    }
    
    @Override
    public List<ConfigInfoWrapper> getConfigsByTenant(String namespaceId) throws NacosApiException {
        // TODO get from nacos servers
        return Collections.emptyList();
    }
}